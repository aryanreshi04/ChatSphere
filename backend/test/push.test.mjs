import {test} from 'node:test';
import assert from 'node:assert/strict';
import {once} from 'node:events';
import {createPushService,tokenId} from '../push.mjs';
import {createAssistantServer} from '../server.mjs';
import {Store} from '../store.mjs';
function fixture(options={}){
 const store=new Store(':memory:'),sent=[],removed=[],records=new Map();let attempts=0;
 const alice='alice-token-fixture-123456789',bob='bob-token-fixture-123456789';
 const repository={
  thread:async()=>({participants:['alice','bob']}),message:async()=>({senderId:options.sender||'alice',type:'text',text:'PRIVATE HUMAN MESSAGE'}),
  hidden:async()=>options.hidden?'m1':undefined,
  devices:async owner=>[{id:tokenId(owner==='alice'?alice:bob),token:owner==='alice'?alice:bob,updatedAt:Date.now()}],
  removeDevice:async(owner,id)=>removed.push({owner,id}),
  ensureTest:async(owner,id)=>{records.set(owner+'/'+id,{owner,id});},
  validTest:async(owner,id)=>records.has(owner+'/'+id)
 };
 const messaging={send:async payload=>{attempts++;if(options.error&&attempts<=options.failCount)throw Object.assign(Error('PRIVATE-FCM-DETAIL'),{code:options.error});sent.push(payload);return 'accepted-fixture';}};
 const waits=[];const service=createPushService({repository,messaging,store,sleep:async ms=>waits.push(ms)});
 return {store,repository,service,sent,removed,records,waits,attempts:()=>attempts};
}
async function request(t,f,body={requestId:'same-id'},token='a',path='/v1/push/test'){
 const server=createAssistantServer({verifyToken:async value=>{if(value==='a'.repeat(24))return {uid:'alice'};throw Error('SECRET');},generate:async()=>'FIXTURE',push:f.service,store:f.store});
 server.listen(0,'127.0.0.1');await once(server,'listening');t.after(()=>{server.closeAllConnections();server.close();});
 return fetch('http://127.0.0.1:'+server.address().port+path,{method:'POST',headers:{authorization:'Bearer '+token.repeat(24),'content-type':'application/json'},body:JSON.stringify(body)});
}
test('self-test push is authenticated, owner-only and rejects caller-controlled recipient/token/message',async t=>{
 const f=fixture();t.after(()=>f.store.close());
 assert.equal((await request(t,f,undefined,'z')).status,401);
 for(const body of [{requestId:'id',recipientId:'bob'},{requestId:'id',token:'other'},{requestId:'id',text:'injected'}])assert.equal((await request(t,f,body)).status,400);
 const response=await request(t,f);assert.equal(response.status,200);assert.equal((await response.json()).accepted,1);
 assert.equal(f.sent[0].data.recipientId,'alice');assert.equal(f.sent[0].data.kind,'test');assert.equal(f.sent[0].notification,undefined);
 assert.deepEqual(Object.keys(f.sent[0].data).sort(),['conversationId','kind','messageId','recipientId']);
});
test('stable test request and repeated incoming message do not produce duplicate sends',async t=>{
 const f=fixture();t.after(()=>f.store.close());
 assert.equal((await request(t,f)).status,200);assert.equal((await request(t,f)).status,200);assert.equal(f.sent.length,1);
 await f.service.deliverMessage('alice_bob','m1');await f.service.deliverMessage('alice_bob','m1');assert.equal(f.sent.length,2);
 assert.equal(f.sent[1].data.recipientId,'bob');assert.doesNotMatch(JSON.stringify(f.sent),/PRIVATE HUMAN MESSAGE/);
});
test('membership validation and hidden conversations prevent unauthorized notification creation',async()=>{
 for(const options of [{sender:'mallory'},{hidden:true}]){const f=fixture(options);try{await f.service.deliverMessage('alice_bob','m1');assert.equal(f.sent.length,0);}finally{f.store.close();}}
});
test('invalid tokens are removed and transient FCM failures retry only within the bound',async()=>{
 const bad=fixture({error:'messaging/registration-token-not-registered',failCount:10});
 try{await bad.service.deliverMessage('alice_bob','m1');assert.equal(bad.removed.length,1);assert.equal(bad.attempts(),1);}finally{bad.store.close();}
 const transient=fixture({error:'messaging/server-unavailable',failCount:2});
 try{await transient.service.deliverMessage('alice_bob','m1');assert.equal(transient.attempts(),3);assert.equal(transient.sent.length,1);assert.equal(transient.waits.length,2);}finally{transient.store.close();}
 const permanent=fixture({error:'messaging/mismatched-credential',failCount:10});
 try{await assert.rejects(()=>permanent.service.deliverMessage('alice_bob','m1'),/FCM sender unavailable/);assert.equal(permanent.attempts(),1);}finally{permanent.store.close();}
});
test('stale or incorrectly bound device records are skipped without exposing tokens',async()=>{
 const f=fixture();try{
 f.repository.devices=async()=>[{id:'wrong-id',token:'some-fixture-token-long-enough',updatedAt:Date.now()},{id:tokenId('expired-fixture-token-1234'),token:'expired-fixture-token-1234',updatedAt:0}];
 await f.service.deliverMessage('alice_bob','m1');assert.equal(f.sent.length,0);
 }finally{f.store.close();}
});
test('self-test rate limits and errors never expose provider exception details',async t=>{
 const f=fixture({error:'messaging/mismatched-credential',failCount:100});t.after(()=>f.store.close());
 for(let n=0;n<5;n++){const response=await request(t,f,{requestId:'id-'+n});assert.equal(response.status,503);assert.doesNotMatch(await response.text(),/PRIVATE-FCM-DETAIL/);}
 assert.equal((await request(t,f,{requestId:'sixth'})).status,429);
});

test('self-test validation is owner-scoped and sends no notification',async()=>{
 const f=fixture();try{
  f.records.set('alice/existing',{owner:'alice'});
  const {Readable}=await import('node:stream');
  const body=()=>Object.assign(Readable.from([Buffer.from(JSON.stringify({requestId:'existing'}))]),{method:'POST',url:'/v1/push/validate',headers:{'content-type':'application/json'}});
  assert.equal((await f.service.handle(body(),{uid:'alice'})).valid,true);
  assert.equal((await f.service.handle(body(),{uid:'bob'})).valid,false);
  assert.equal(f.sent.length,0);
 }finally{f.store.close();}
});

test('saved-message notification dispatch enforces sender identity and deduplicates without an index',async t=>{
 const f=fixture();t.after(()=>f.store.close());
 const body={conversationId:'alice_bob',messageId:'m1'};
 for(let n=0;n<2;n++)assert.equal((await request(t,f,body,'a','/v1/push/message')).status,200);
 assert.equal(f.sent.length,1);assert.equal(f.sent[0].data.recipientId,'bob');
 assert.equal((await request(t,f,{...body,recipientId:'mallory'},'a','/v1/push/message')).status,400);
 f.repository.message=async()=>({senderId:'bob',type:'text',text:'private'});
 assert.equal((await request(t,f,body,'a','/v1/push/message')).status,403);
 f.repository.message=async()=>({senderId:'alice',type:'text',text:'private'});f.repository.thread=async()=>({participants:['mallory','alice']});
 assert.equal((await request(t,f,body,'a','/v1/push/message')).status,200);
 f.repository.thread=async()=>({participants:['bob','mallory']});
 assert.equal((await request(t,f,body,'a','/v1/push/message')).status,403);
 assert.doesNotMatch(JSON.stringify(f.sent),/private/);
});
