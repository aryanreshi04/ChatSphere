import {test} from 'node:test';
import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {once} from 'node:events';
import {randomUUID,createHash} from 'node:crypto';
import {createAssistantServer} from '../server.mjs';
import {createMediaService,firestoreMediaRepository} from '../media.mjs';
import {Store} from '../store.mjs';
const ready=process.env.FIREBASE_PROJECT_ID==='demo-chatsphere'&&process.env.FIREBASE_AUTH_EMULATOR_HOST==='127.0.0.1:9099'&&process.env.FIRESTORE_EMULATOR_HOST==='127.0.0.1:8080';
test('real Firebase emulator auth/transaction authorizes larger media with a storage fixture',{skip:!ready},async t=>{
 const {auth,firestore}=await import('../firebase.mjs');
 const {FieldValue}=await import('firebase-admin/firestore');
 const signup=async()=>{
  const response=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:randomUUID()+'@example.test',password:'DemoPassword123!',returnSecureToken:true})});
  assert.equal(response.status,200);return response.json();
 };
 const a=await signup(),b=await signup(),conversation=[a.localId,b.localId].sort().join('_'),id=randomUUID();
 await firestore.doc('conversations/'+conversation).set({participants:[a.localId,b.localId],lastMessage:'',lastMessageTime:0,lastMessageId:''});
 const objects=new Map();const storage={putOnce:async(path,source)=>{if(!objects.has(path))objects.set(path,await readFile(source.file))},signedDownload:async(path)=>'https://fixture.supabase.co/'+path};
 const store=new Store(':memory:');
 const server=createAssistantServer({verifyToken:token=>auth.verifyIdToken(token,true),generate:async()=>'TEST FIXTURE',
  media:createMediaService({storage,repository:firestoreMediaRepository(firestore,()=>FieldValue.serverTimestamp())}),store});
 server.listen(0,'127.0.0.1');await once(server,'listening');t.after(()=>{server.closeAllConnections();server.close();store.close()});
 const bytes=Buffer.alloc(300_000,42);Buffer.from([137,80,78,71,13,10,26,10]).copy(bytes);
 const url='http://127.0.0.1:'+server.address().port+'/v1/media/upload/'+conversation+'/'+id;
 const post=()=>fetch(url,{method:'POST',headers:{authorization:'Bearer '+a.idToken,'content-type':'image/png','x-media-sha256':createHash('sha256').update(bytes).digest('hex'),'x-message-timestamp':String(Date.now())},body:bytes});
 assert.equal((await post()).status,200);assert.equal((await post()).status,200);
 const message=(await firestore.doc('conversations/'+conversation+'/messages/'+id).get()).data();
 assert.equal(message.senderId,a.localId);assert.equal(message.mediaSize,300_000);assert.equal(message.mediaPath,conversation+'/'+id);
 assert.equal(message.imageBase64,undefined);assert.equal(objects.size,1);assert.equal((await firestore.collection('conversations/'+conversation+'/messages').get()).size,1);
 assert.equal((await firestore.doc('conversations/'+conversation).get()).data().lastMessageId,id);
});
