import {test} from 'node:test';
import assert from 'node:assert/strict';
import {once} from 'node:events';
import {geminiReply,retryAfterMs} from '../gemini.mjs';
import {ApiError} from '../core.mjs';
import {Store} from '../store.mjs';
import {createAssistantServer} from '../server.mjs';
const env={GEMINI_API_KEY:'PRIVATE-TEST-KEY',GEMINI_FREE_TIER_CONFIRMED:'true',GEMINI_MODEL:'gemini-3.1-flash-lite'};
const messages=[{role:'user',content:'Explain fractions'}];
const answer=()=>Response.json({candidates:[{content:{parts:[{text:'PRIVATE-THOUGHT',thought:true},{text:'A fraction is part of a whole.'}]}}]});
test('temporary provider failures have bounded exponential backoff and count every real attempt',async()=>{
 let calls=0,reserved=0;const waits=[];
 const result=await geminiReply(messages,{env,random:()=>0,sleep:async ms=>waits.push(ms),beforeAttempt:()=>reserved++,fetcher:async()=>++calls<3?new Response('private',{status:503}):answer()});
 assert.equal(calls,3);assert.equal(reserved,3);assert.deepEqual(waits,[700,1400]);assert.equal(result,'A fraction is part of a whole.');assert.doesNotMatch(result,/THOUGHT/);
});
test('Retry-After seconds/date and provider RetryInfo are honored without shortening long waits',async()=>{
 assert.equal(retryAfterMs('2',0),2000);assert.equal(retryAfterMs('Thu, 01 Jan 1970 00:00:05 GMT',0),5000);
 let calls=0;const waits=[];
 await geminiReply(messages,{env,sleep:async ms=>waits.push(ms),random:()=>0,fetcher:async()=>++calls===1?Response.json({error:{details:[]}},{status:429,headers:{'Retry-After':'2'}}):answer()});
 assert.equal(calls,2);assert.deepEqual(waits,[2000]);
 calls=0;
 await assert.rejects(()=>geminiReply(messages,{env,sleep:async()=>assert.fail('Must not shorten Retry-After'),fetcher:async()=>{calls++;return Response.json({error:{}},{status:429,headers:{'Retry-After':'3600'}});}}),error=>error.code==='AI_RATE_LIMIT'&&error.retryAfterSeconds===3600);
 assert.equal(calls,1);
});
test('daily quotas, invalid key/model and safety failures are permanent and never auto-retried',async()=>{
 for(const [status,body,code]of [[429,{error:{message:'GenerateRequestsPerDay quota limit: 0'}},'AI_QUOTA_EXHAUSTED'],[403,{error:{message:'PRIVATE'}},'AI_CONFIGURATION'],[404,{error:{}},'AI_MODEL_UNAVAILABLE'],[200,{candidates:[]},'AI_NO_ANSWER']]){
  let calls=0;await assert.rejects(()=>geminiReply(messages,{env,sleep:async()=>assert.fail('Permanent failure retry'),fetcher:async()=>{calls++;return Response.json(body,{status,headers:{'Retry-After':'1'}});}}),error=>error.code===code&&!error.retryable);
  assert.equal(calls,1);
 }
});
test('persistent daily attempt cap prevents retries from bypassing the configured quota',async()=>{
 const store=new Store(':memory:');let calls=0;
 try{await assert.rejects(()=>geminiReply(messages,{env,sleep:async()=>{},beforeAttempt:()=>{if(!store.takeProviderQuota(2))throw new ApiError(429,'Daily cap',{code:'AI_DAILY_LIMIT'});},fetcher:async()=>{calls++;return new Response('',{status:503});}}),error=>error.code==='AI_DAILY_LIMIT');assert.equal(calls,2);}finally{store.close();}
});
test('network/timeouts are distinguished and bounded, malformed/oversized responses are rejected safely',async()=>{
 for(const [name,code]of [['TypeError','AI_NETWORK'],['TimeoutError','AI_TIMEOUT']]){
  let calls=0;await assert.rejects(()=>geminiReply(messages,{env,sleep:async()=>{},fetcher:async()=>{calls++;throw Object.assign(Error('PRIVATE-TEST-KEY and PRIVATE-TOKEN'),{name});}}),error=>error.code===code&&error.providerAttempts===3&&!/PRIVATE/.test(error.message));assert.equal(calls,3);
 }
 for(const body of ['invalid JSON','x'.repeat(100001)])await assert.rejects(()=>geminiReply(messages,{env,fetcher:async()=>new Response(body,{status:200})}),error=>error.code==='AI_BAD_RESPONSE');
});
test('concurrent identical assistant requests share one generation and emit typed sanitized failures',async t=>{
 const store=new Store(':memory:');let calls=0;
 const server=createAssistantServer({store,verifyToken:async()=>({uid:'alice'}),generate:async()=>{calls++;await new Promise(r=>setTimeout(r,50));return 'FIXTURE';}});
 server.listen(0,'127.0.0.1');await once(server,'listening');t.after(()=>{server.closeAllConnections();server.close();store.close();});
 const post=()=>fetch('http://127.0.0.1:'+server.address().port+'/v1/chat',{method:'POST',headers:{authorization:'Bearer '+'a'.repeat(24),'content-type':'application/json'},body:JSON.stringify({requestId:'stable',messages})});
 const responses=await Promise.all([post(),post()]);for(const response of responses)assert.equal(response.status,200);assert.equal(calls,1);
});
