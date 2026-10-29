import {createHash} from 'node:crypto';
import {ApiError,RateLimiter,recipients} from './core.mjs';
import {setTimeout as pause} from 'node:timers/promises';
const invalidCodes=new Set(['messaging/registration-token-not-registered','messaging/invalid-registration-token']);
const retryCodes=new Set(['messaging/internal-error','messaging/server-unavailable']);
export const tokenId=token=>createHash('sha256').update(token).digest('hex');
export function createPushService({repository,messaging,store,sleep=pause,now=Date.now}) {
 const limiter=new RateLimiter();const messageLimiter=new RateLimiter({perMinute:60,perDay:1000});const active=new Map();
 async function sendDevice(owner,device,data,key){
  const token=device.token;
  if(typeof token!=='string'||token.length<20||token.length>4096||device.id!==tokenId(token)||!Number.isFinite(device.updatedAt)||device.updatedAt<now()-30*86400000)return {skipped:1};
  const receipt=key+'/'+tokenId(token);
  if(store.notified(receipt))return {deduplicated:1};
  if(active.has(receipt))return active.get(receipt);
  const work=(async()=>{
   for(let attempt=0;attempt<3;attempt++){
    try{
     await messaging.send({token,data,android:{priority:'high',ttl:3600000}});
     store.markNotified(receipt);return {accepted:1};
    }catch(error){
     if(invalidCodes.has(error?.code)){await repository.removeDevice(owner,device.id);return {invalid:1};}
     if(!retryCodes.has(error?.code)||attempt===2)throw new ApiError(503,'FCM sender unavailable. Check Firebase Admin permissions, Messaging API and connection.');
     await sleep(500*2**attempt+Math.floor(Math.random()*200));
    }
   }
  })();
  active.set(receipt,work);try{return await work;}finally{active.delete(receipt);}
 }
 async function deliver(owner,data,key){
  const devices=(await repository.devices(owner)).slice(0,20);
  const result={registeredDevices:devices.length,accepted:0,deduplicated:0,invalid:0,skipped:0};
  for(const device of devices){const outcome=await sendDevice(owner,device,data,key);for(const [name,count]of Object.entries(outcome))result[name]+=count;}
  return result;
 }
 return {
  async handle(request,identity){
   if(request.method!=='POST'||!['/v1/push/test','/v1/push/validate','/v1/push/message'].includes(request.url)||!request.headers['content-type']?.startsWith('application/json'))throw new ApiError(400,'Use the authenticated test-push endpoint.');
   let bytes=0;const chunks=[];for await(const chunk of request){bytes+=chunk.length;if(bytes>1024)throw new ApiError(413,'Test request is too large.');chunks.push(chunk);}
   let body;try{body=JSON.parse(Buffer.concat(chunks).toString());}catch{throw new ApiError(400,'Invalid test request.');}
   if(request.url==='/v1/push/message'){
    if(!body||Array.isArray(body)||Object.keys(body).some(k=>!['conversationId','messageId'].includes(k))||!/^[A-Za-z0-9_-]{1,260}$/.test(body.conversationId||'')||!/^[A-Za-z0-9_-]{1,128}$/.test(body.messageId||''))throw new ApiError(400,'Use the saved conversation and message IDs only.');
    messageLimiter.take(identity.uid);
    const [thread,message]=await Promise.all([repository.thread(body.conversationId),repository.message(body.conversationId,body.messageId)]);
    if(!message||message.senderId!==identity.uid||recipients(thread,message).length!==1)throw new ApiError(403,'Only the saved message sender may request its notification.');
    await this.deliverMessage(body.conversationId,body.messageId);
    return {acceptedForDelivery:true};
   }
   if(!body||Array.isArray(body)||Object.keys(body).some(k=>k!=='requestId')||!/^[A-Za-z0-9-]{1,80}$/.test(body.requestId||''))throw new ApiError(400,'Only a request ID is accepted; test pushes target your own devices.');
   limiter.take(identity.uid);
   if(request.url==='/v1/push/validate')return {valid:await repository.validTest(identity.uid,body.requestId,now())};
   await repository.ensureTest(identity.uid,body.requestId,now());
   const data={recipientId:identity.uid,conversationId:'notification-test',messageId:body.requestId,kind:'test'};
   return {requestId:body.requestId,...await deliver(identity.uid,data,'test/'+identity.uid+'/'+body.requestId)};
  },
  async deliverMessage(conversation,id){
   if(!/^[A-Za-z0-9_-]{1,260}$/.test(conversation)||!/^[A-Za-z0-9_-]{1,128}$/.test(id))return;
   const [thread,message]=await Promise.all([repository.thread(conversation),repository.message(conversation,id)]);
   if(!message||!['text','image','location','video','audio'].includes(message.type||'text')||typeof message.text!=='string'||message.text.length>4000)return;
   for(const recipient of recipients(thread,message)){
    if(await repository.hidden(recipient,conversation)===id)continue;
    await deliver(recipient,{recipientId:recipient,conversationId:conversation,messageId:id,kind:'message'},'message/'+recipient+'/'+conversation+'/'+id);
   }
  }
 };
}
export function firestorePushRepository(db,FieldValue,Timestamp){
 const thread=id=>db.doc('conversations/'+id);
 return {
  thread:async id=>(await thread(id).get()).data(),
  message:async(c,id)=>(await thread(c).collection('messages').doc(id).get()).data(),
  hidden:async(owner,c)=>(await db.doc('users/'+owner+'/hiddenChats/'+c).get()).get('hiddenMessageId'),
  devices:async owner=>(await db.collection('users/'+owner+'/devices').limit(20).get()).docs.map(doc=>({id:doc.id,token:doc.get('token'),updatedAt:doc.get('updatedAt')?.toMillis?.()})),
  removeDevice:(owner,id)=>db.doc('users/'+owner+'/devices/'+id).delete(),
  validTest:async(owner,id,now)=>{const doc=await db.doc('users/'+owner+'/pushTests/'+id).get();return doc.exists&&doc.get('owner')===owner&&doc.get('kind')==='test'&&(doc.get('expiresAt')?.toMillis?.()||0)>now;},
  ensureTest:(owner,id,now)=>db.runTransaction(async tx=>{
   const ref=db.doc('users/'+owner+'/pushTests/'+id),existing=await tx.get(ref);
   if(existing.exists){if(existing.get('expiresAt')?.toMillis?.()<now)throw new ApiError(409,'Test request expired. Start a new test.');return;}
   tx.create(ref,{kind:'test',owner,createdAt:FieldValue.serverTimestamp(),expiresAt:Timestamp.fromMillis(now+3600000)});
  })
 };
}
