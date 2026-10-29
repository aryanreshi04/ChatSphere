// Explicitly gated local-only verification broker. Never used by npm start or deployed.
import {createServer} from 'node:http';
import {randomUUID,randomBytes,timingSafeEqual} from 'node:crypto';
import {writeFile,readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createAssistantServer} from '../server.mjs';
import {createPushService,firestorePushRepository} from '../push.mjs';
import {geminiReply} from '../gemini.mjs';
import {Store} from '../store.mjs';
import {ApiError} from '../core.mjs';
if(process.env.RUN_LIVE_PUSH!=='true'||process.env.FIRESTORE_EMULATOR_HOST||process.env.FIREBASE_AUTH_EMULATOR_HOST||!process.env.LIVE_PUSH_ACCESS_FILE)throw Error('Explicit live-push verification configuration required.');
const client=JSON.parse(await readFile(new URL('../../app/google-services.json',import.meta.url),'utf8'));
if(client.project_info.project_id!==process.env.FIREBASE_PROJECT_ID)throw Error('Firebase client/backend projects differ; verification stopped.');
const {auth,firestore,messaging}=await import('../firebase.mjs');
const {FieldValue,Timestamp}=await import('firebase-admin/firestore');
const nonce=randomBytes(32).toString('hex');
await writeFile(resolve(process.env.LIVE_PUSH_ACCESS_FILE),nonce,{flag:'wx',mode:0o600});
const run=randomUUID(),created=[];let alice,bob,conversation,messages=0,cleaned=false;
const apiKey=client.client.find(entry=>entry.client_info?.android_client_info?.package_name==='com.jos.myapplication')?.api_key?.[0]?.current_key;
if(!apiKey)throw Error('Existing Firebase Android client key unavailable');
async function account(action,body){
 const response=await fetch('https://identitytoolkit.googleapis.com/v1/accounts:'+action+'?key='+encodeURIComponent(apiKey),{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
 if(!response.ok)throw new ApiError(503,'Synthetic client-account setup unavailable',{code:'VERIFICATION_AUTH_'+response.status});return response.status===200?response.json():{};
}
const store=new Store(':memory:');
const repository=firestorePushRepository(firestore,FieldValue,Timestamp);
const push=createPushService({repository,messaging,store});
const daily=Number(process.env.GEMINI_DAILY_REQUEST_LIMIT||40);
const providerQuota=new Store(process.env.STATE_DATABASE||'./runtime/state.sqlite3');
const api=createAssistantServer({store,push,verifyToken:async token=>{const identity=await auth.verifyIdToken(token,true);if(![alice,bob].includes(identity.uid))throw Error('Verification users only');return identity;},
 generate:content=>geminiReply(content,{beforeAttempt:()=>{if(!providerQuota.takeProviderQuota(daily))throw new ApiError(429,'Verification AI daily cap reached',{code:'AI_DAILY_LIMIT'});}})});
async function setup(){
 for(const role of ['a','b']){
  const email='cs-push-'+role+'-'+run+'@example.test',password=randomBytes(24).toString('base64url');
  const result=await account('signUp',{email,password,returnSecureToken:true});
  created.push({uid:result.localId,email,password,idToken:result.idToken});
 }
 alice=created[0].uid;bob=created[1].uid;conversation=[alice,bob].sort().join('_');
 await firestore.doc('conversations/'+conversation).create({participants:[alice,bob],lastMessage:'',lastMessageTime:0,lastMessageId:''});
}
async function cleanup(){
 if(cleaned)return;
 await setupPromise?.catch(()=>{});
 if(conversation){
  const parent=firestore.doc('conversations/'+conversation),thread=await parent.get();
  if(thread.exists&&JSON.stringify(thread.get('participants'))===JSON.stringify([alice,bob]))await firestore.recursiveDelete(parent);
 }
 while(created.length){
  const user=created.at(-1);
  await firestore.recursiveDelete(firestore.doc('users/'+user.uid));
  await account('delete',{idToken:user.idToken});created.pop();
 }
 cleaned=true;console.log('Verification fixtures cleaned; existing users/conversations were not touched.');
}

let setupPromise;
const server=createServer(async(request,response)=>{
 response.setHeader('Content-Type','application/json');response.setHeader('Cache-Control','no-store');
 const reply=(status,body)=>{response.writeHead(status);response.end(JSON.stringify(body));};
 try{
  if(request.url.startsWith('/v1/'))return api.emit('request',request,response);
  const supplied=Buffer.from(request.headers['x-verification-key']||'');
  if(supplied.length!==nonce.length||!timingSafeEqual(supplied,Buffer.from(nonce)))return reply(401,{error:'Verification access required'});
  if(request.url==='/bootstrap'){
   setupPromise??=setup();await setupPromise;
   return reply(200,{aliceEmail:created[0].email,alicePassword:created[0].password,bobEmail:created[1].email,bobPassword:created[1].password,conversation});
  }
  if(['/message','/message?dispatch=client'].includes(request.url)&&request.method==='POST'){
   await setupPromise;if(++messages>6)return reply(429,{error:'Verification message limit'});
   const id=randomUUID(),text='Synthetic study fixture: please submit the homework assignment before class deadline',timestamp=Date.now();
   const parent=firestore.doc('conversations/'+conversation);
   const batch=firestore.batch();batch.create(parent.collection('messages').doc(id),{senderId:bob,text,type:'text',timestamp,serverTimestamp:FieldValue.serverTimestamp()});
   batch.update(parent,{lastMessage:text,lastMessageId:id,lastMessageTime:timestamp});await batch.commit();
   if(request.url==='/message'){await push.deliverMessage(conversation,id);console.log('Real FCM accepted a synthetic fixture message.');}
   return reply(200,{owner:alice,conversation,message:id,text});
  }
  if(request.url==='/state')return reply(200,{owner:alice,conversation});
  if(request.url==='/cleanup'&&request.method==='POST'){await cleanup();return reply(200,{cleaned:true});}
  reply(404,{error:'Not found'});
 }catch(error){console.error('Live verification failure code: '+(error instanceof ApiError?error.code:typeof error?.code==='string'?error.code.replace(/[^A-Za-z0-9/_-]/g,'').slice(0,100):'unavailable'));reply(error instanceof ApiError?error.status:503,{error:'Live verification unavailable; inspect safe status and external configuration.'});}
});
server.listen(8790,'127.0.0.1',()=>console.log('Local live verification broker ready on loopback; no credentials are logged.'));
async function stop(){try{await cleanup();}catch{console.error('Fixture cleanup pending; retain this test run for exact fixture IDs.');}server.closeAllConnections();server.close();store.close();providerQuota.close();process.exit(0);}
process.on('SIGINT',()=>void stop());process.on('SIGTERM',()=>void stop());
