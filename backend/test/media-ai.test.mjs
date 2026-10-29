import {test} from 'node:test';
import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {once} from 'node:events';
import {createHash} from 'node:crypto';
import {createAssistantServer} from '../server.mjs';
import {createMediaService,mediaLimit,imageMime,supabaseStorage} from '../media.mjs';
import {geminiReply} from '../gemini.mjs';
import {Store} from '../store.mjs';
const hash=b=>createHash('sha256').update(b).digest('hex');
const image=n=>{const b=Buffer.alloc(n,42);Buffer.from([137,80,78,71,13,10,26,10]).copy(b);return b;};
async function fixture(t,options={}){
  const objects=new Map(),messages=new Map();let commits=0,failCommit=!!options.failCommit;
  const repository={
    thread:async()=>({participants:['alice','bob']}),
    message:async(c,id)=>messages.get(c+'/'+id),
    commit:async(c,id,msg,check)=>{if(failCommit){failCommit=false;throw Error('fixture failure after object upload');}
      const key=c+'/'+id;if(messages.has(key))check(messages.get(key));else{messages.set(key,msg);commits++;}}
  };
  const storage={
    putOnce:async(path,source,mime,signal)=>{if(options.upload)await options.upload(signal);
      const bytes=await readFile(source.file);
      if(objects.has(path))assert.equal(hash(objects.get(path)),hash(bytes));else objects.set(path,bytes);},
    signedDownload:async(path,seconds)=>{assert.equal(seconds,60);if(options.downloadFailure)throw Error('private-storage-fixture-error');return 'https://project.supabase.co/storage/v1/object/sign/chat-media/'+path+'?token=FIXTURE';}
  };
  const media=createMediaService({storage,repository,maxBytes:options.limit||1_000_000});
  const store=new Store(':memory:');
  const server=createAssistantServer({verifyToken:async token=>({uid:token[0]==='a'?'alice':token[0]==='b'?'bob':'mallory'}),generate:async()=>'TEST FIXTURE',media,store});
  server.listen(0,'127.0.0.1');await once(server,'listening');t.after(()=>{server.closeAllConnections();server.close();store.close();});
  const base='http://127.0.0.1:'+server.address().port;
  const upload=(bytes,id='photo1',changes={})=>fetch(base+'/v1/media/upload/alice_bob/'+id,{
    method:'POST',headers:{authorization:'Bearer '+'a'.repeat(24),'content-type':'image/png','x-media-sha256':hash(bytes),'x-message-timestamp':String(Date.now()),...changes},body:bytes});
  const download=(id='photo1',token='b')=>fetch(base+'/v1/media/download',{method:'POST',headers:{authorization:'Bearer '+token.repeat(24),'content-type':'application/json'},body:JSON.stringify({conversationId:'alice_bob',messageId:id})});
  return {base,objects,messages,upload,download,commits:()=>commits};
}
test('media configuration accepts exact Free ceiling and rejects invalid limits and disguised files',()=>{
  assert.equal(mediaLimit(),50_000_000);assert.equal(mediaLimit(50_000_000),50_000_000);
  for(const n of [0,-1,50_000_001,NaN,1.5])assert.throws(()=>mediaLimit(n));
  assert.equal(imageMime(image(32)),'image/png');
  assert.throws(()=>imageMime(Buffer.from('<html>fake.png</html>')));
});
test('HTTP larger image transport accepts exact configured boundary and rejects boundary+1, wrong types and checksums',async t=>{
  const f=await fixture(t);const bytes=image(1_000_000);
  assert.equal((await f.upload(bytes)).status,200);assert.equal(f.objects.values().next().value.length,1_000_000);
  assert.equal((await f.upload(image(1_000_001),'large')).status,413);
  assert.equal((await f.upload(Buffer.from('<html>fake image</html>'),'invalid')).status,415);
  assert.equal((await f.upload(image(32),'hash',{'x-media-sha256':'0'.repeat(64)})).status,415);
  assert.equal((await f.upload(image(32),'audio',{'content-type':'audio/mpeg'})).status,415);
});
test('retry after a failure between upload and Firestore commit preserves one object and one message',async t=>{
  const f=await fixture(t,{failCommit:true});const bytes=image(200_000);
  assert.equal((await f.upload(bytes)).status,503);assert.equal(f.objects.size,1);assert.equal(f.commits(),0);
  assert.equal((await f.upload(bytes)).status,200);assert.equal((await f.upload(bytes)).status,200);
  assert.equal(f.objects.size,1);assert.equal(f.commits(),1);
  assert.equal((await f.upload(image(200_001))).status,409);
});
test('private downloads require membership and a message-bound object path; failures do not leak provider errors',async t=>{
  const f=await fixture(t);assert.equal((await f.upload(image(200_000))).status,200);
  const authorized=await f.download();assert.equal(authorized.status,200);assert.equal((await authorized.json()).expiresIn,60);
  assert.equal((await f.download('photo1','m')).status,403);
  assert.equal((await f.download('missing')).status,404);
  f.messages.get('alice_bob/photo1').mediaPath='another_conversation/private-photo';
  assert.equal((await f.download()).status,404);
  const failure=await fixture(t,{downloadFailure:true});await failure.upload(image(200_000));
  const result=await failure.download();assert.equal(result.status,503);assert.doesNotMatch(await result.text(),/private-storage-fixture-error/);
});
test('disconnect during storage upload aborts upstream and leaves the message uncommitted',async t=>{
  let started;const ready=new Promise(r=>started=r);let aborted=false;
  const f=await fixture(t,{upload:signal=>new Promise((resolve,reject)=>{started();signal.addEventListener('abort',()=>{aborted=true;reject(Error('cancel fixture'));},{once:true});})});
  const bytes=image(200_000),cancel=new AbortController();
  const pending=fetch(f.base+'/v1/media/upload/alice_bob/cancelled',{method:'POST',signal:cancel.signal,headers:{authorization:'Bearer '+'a'.repeat(24),'content-type':'image/png','x-media-sha256':hash(bytes),'x-message-timestamp':String(Date.now())},body:bytes});
  const failed=assert.rejects(pending,{name:'AbortError'});
  await ready;cancel.abort();await failed;
  for(let n=0;n<50&&!aborted;n++)await new Promise(r=>setTimeout(r,20));
  assert.equal(aborted,true);assert.equal(f.objects.size,0);assert.equal(f.commits(),0);
});

test('Supabase adapter reports upload/download errors safely and never enables upsert',async()=>{
  const env={SUPABASE_URL:'https://project.supabase.co',SUPABASE_SERVICE_ROLE_KEY:'SECRET',SUPABASE_MEDIA_BUCKET:'chat-media'};
  const adapter=supabaseStorage(env,async(url,request)=>{if(request.method===undefined)return Response.json({public:false});assert.equal(request.headers['x-upsert'],request.method==='POST'&&!url.includes('/sign/')?'false':undefined);return new Response('{}',{status:503});});
  await assert.rejects(()=>adapter.putOnce('alice_bob/photo',image(200_000),'image/png'),/Supabase upload failed/);
  await assert.rejects(()=>adapter.signedDownload('alice_bob/photo',60),/authorize the media download/);
});
test('Gemini provider uses only assistant text and backend key, and handles quotas/configuration/safety',async()=>{
  const env={GEMINI_API_KEY:'PRIVATE-KEY',GEMINI_FREE_TIER_CONFIRMED:'true'};
  const calls=[];
  const provider=async(url,request)=>{calls.push({url,request});return Response.json({candidates:[{content:{parts:[{text:'TEST FIXTURE answer'}]}}]});};
  assert.equal(await geminiReply([{role:'user',content:'Explain fractions'}],{env,fetcher:provider}),'TEST FIXTURE answer');
  const sent=JSON.parse(calls[0].request.body);
  assert.equal(calls[0].request.headers['x-goog-api-key'],'PRIVATE-KEY');
  assert.doesNotMatch(calls[0].request.body,/PRIVATE-KEY|Firebase|Bearer/);
  assert.deepEqual(sent.contents,[{role:'user',parts:[{text:'Explain fractions'}]}]);
  await assert.rejects(()=>geminiReply([],{env:{},fetcher:provider}),/key is missing/);
  await assert.rejects(()=>geminiReply([],{env,fetcher:async()=>new Response('PRIVATE-ERROR',{status:429})}),/quota or rate limit/);
  await assert.rejects(()=>geminiReply([],{env,fetcher:async()=>new Response('PRIVATE-ERROR',{status:403})}),/key or account eligibility/);
  await assert.rejects(()=>geminiReply([],{env,fetcher:async()=>{throw Error('SECRET-NETWORK');}}),/timed out/);
  await assert.rejects(()=>geminiReply([],{env,fetcher:async()=>Response.json({candidates:[]})}),/no answer/);
});
test('provider daily request counter survives repeated calls and resets on a new UTC day',()=>{
  const store=new Store(':memory:');try{const day=new Date('2026-10-25T01:00:00Z');
    assert.equal(store.takeProviderQuota(2,day),true);assert.equal(store.takeProviderQuota(2,day),true);assert.equal(store.takeProviderQuota(2,day),false);
    assert.equal(store.takeProviderQuota(2,new Date('2026-10-26T01:00:00Z')),true);
  }finally{store.close();}
});

test('Supabase adapter refuses a public bucket before uploading or signing',async()=>{
 let calls=0;
 const adapter=supabaseStorage({SUPABASE_URL:'https://project.supabase.co',SUPABASE_SERVICE_ROLE_KEY:'SECRET'},async()=>{calls++;return Response.json({public:true})});
 await assert.rejects(()=>adapter.putOnce('alice_bob/photo',image(200000),'image/png'),/must be private/);
 await assert.rejects(()=>adapter.signedDownload('alice_bob/photo',60),/must be private/);
 assert.equal(calls,2);
});
