import {test} from 'node:test';
import assert from 'node:assert/strict';
import {once} from 'node:events';
import {randomUUID} from 'node:crypto';
import {createReadStream} from 'node:fs';
import {mkdtemp,open,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createAssistantServer} from '../server.mjs';
import {createMediaService,supabaseStorage,firestoreMediaRepository} from '../media.mjs';
import {hashStream} from '../media-files.mjs';
import {Store} from '../store.mjs';
const ready=process.env.RUN_LIVE_MEDIA==='true'&&process.env.FIREBASE_PROJECT_ID==='demo-chatsphere'&&process.env.FIREBASE_AUTH_EMULATOR_HOST==='127.0.0.1:9099'&&process.env.FIRESTORE_EMULATOR_HOST==='127.0.0.1:8080'&&!!process.env.SUPABASE_URL&&!!process.env.SUPABASE_SERVICE_ROLE_KEY;

test('real private Supabase accepts and retrieves an exact 50,000,000-byte file through Firebase-authorized backend',{skip:!ready,timeout:600000},async t=>{
 const {auth,firestore}=await import('../firebase.mjs');const {FieldValue}=await import('firebase-admin/firestore');
 const signup=async()=>{const response=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:randomUUID()+'@example.test',password:'DemoPassword123!',returnSecureToken:true})});assert.equal(response.status,200);return response.json();};
 const a=await signup(),b=await signup(),outsider=await signup(),conversation=[a.localId,b.localId].sort().join('_'),id=randomUUID();
 await firestore.doc('conversations/'+conversation).set({participants:[a.localId,b.localId],lastMessage:'',lastMessageTime:0,lastMessageId:''});
 const store=new Store(':memory:');const server=createAssistantServer({verifyToken:token=>auth.verifyIdToken(token,true),generate:async()=>{throw Error('AI is not used in media tests');},store,
  media:createMediaService({storage:supabaseStorage(),repository:firestoreMediaRepository(firestore,()=>FieldValue.serverTimestamp())})});
 server.listen(0,'127.0.0.1');await once(server,'listening');t.after(()=>{server.closeAllConnections();server.close();store.close();});
 const directory=await mkdtemp(join(tmpdir(),'chatsphere-live-fixture-'));t.after(()=>rm(directory,{recursive:true,force:true}));
 const file=join(directory,'boundary.wav'),size=50_000_000;const header=Buffer.alloc(44);
 header.write('RIFF');header.writeUInt32LE(size-8,4);header.write('WAVEfmt ',8);header.writeUInt32LE(16,16);header.writeUInt16LE(1,20);header.writeUInt16LE(1,22);header.writeUInt32LE(16000,24);header.writeUInt32LE(32000,28);header.writeUInt16LE(2,32);header.writeUInt16LE(16,34);header.write('data',36);header.writeUInt32LE(size-44,40);
 const handle=await open(file,'wx');try{await handle.write(header);await handle.truncate(size);}finally{await handle.close();}
 const checksum=await hashStream(createReadStream(file),size);const base='http://127.0.0.1:'+server.address().port;
 const upload=()=>fetch(base+'/v1/media/upload/'+conversation+'/'+id,{method:'POST',headers:{authorization:'Bearer '+a.idToken,'content-type':'audio/wav','content-length':String(size),'x-media-sha256':checksum.sha256,'x-message-timestamp':String(Date.now()),'x-media-name':'synthetic-50MB-boundary.wav'},body:createReadStream(file),duplex:'half',signal:AbortSignal.timeout(600000)});
 const response=await upload();assert.equal(response.status,200,await response.clone().text());assert.equal((await response.json()).media.mediaSize,size);
 const download=token=>fetch(base+'/v1/media/download',{method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},body:JSON.stringify({conversationId:conversation,messageId:id})});
 assert.equal((await download(outsider.idToken)).status,403);
 const authorization=await download(b.idToken);assert.equal(authorization.status,200);const signed=await authorization.json();assert.equal(signed.size,size);
 const retrieved=await fetch(signed.url,{redirect:'error',signal:AbortSignal.timeout(600000)});assert.equal(retrieved.status,200);assert.deepEqual(await hashStream(retrieved.body,size),checksum);
 assert.equal((await upload()).status,200);assert.equal((await firestore.collection('conversations/'+conversation+'/messages').get()).size,1);
 console.log('LIVE_STORAGE_VERIFIED bytes='+size+' object='+conversation+'/'+id+' (synthetic private fixture retained; no production Firestore data used)');
});
