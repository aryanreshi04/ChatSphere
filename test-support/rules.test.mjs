import {readFileSync} from 'node:fs';
import {before,after,beforeEach,test} from 'node:test';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,getDocs,collection,query,where,writeBatch,serverTimestamp,updateDoc,deleteDoc} from 'firebase/firestore';
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-chatsphere',firestore:{host:'127.0.0.1',port:8080,rules:readFileSync(new URL('../firestore.rules',import.meta.url),'utf8')}});});
after(async()=>{await env?.cleanup();});
beforeEach(async()=>{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context=>{
    const db=context.firestore();
    for(const uid of ['alice','bob','mallory']) await setDoc(doc(db,'users',uid),{name:uid,email:uid+'@example.test'});
    await setDoc(doc(db,'conversations/alice_bob'),{participants:['alice','bob'],lastMessage:'',lastMessageId:'',lastMessageTime:1});
  });
});
const db=uid=>env.authenticatedContext(uid,{email:uid+'@example.test'}).firestore();
function send(database,id='m1',data={}) {
  const batch=writeBatch(database);
  batch.set(doc(database,'conversations/alice_bob/messages/'+id),{senderId:'alice',text:'Hello',timestamp:2,type:'text',serverTimestamp:serverTimestamp(),...data});
  batch.update(doc(database,'conversations/alice_bob'),{lastMessage:'Hello',lastMessageTime:2,lastMessageId:id});
  return batch.commit();
}
test('unauthenticated users cannot read directory or chats',async()=>{
  const database=env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(database,'users/alice')));await assertFails(getDoc(doc(database,'conversations/alice_bob')));
});
test('only participants may read conversation/messages/media',async()=>{
  await assertSucceeds(send(db('alice')));
  await assertSucceeds(getDoc(doc(db('bob'),'conversations/alice_bob/messages/m1')));
  for(const path of ['conversations/alice_bob','conversations/alice_bob/messages/m1','conversations/alice_bob/media/m1']) await assertFails(getDoc(doc(db('mallory'),path)));
});
test('participant-filtered list is permitted; unrestricted list is denied',async()=>{
  await assertSucceeds(getDocs(query(collection(db('alice'),'conversations'),where('participants','array-contains','alice'))));
  await assertFails(getDocs(collection(db('alice'),'conversations')));
});
test('sender spoofing and membership changes are rejected',async()=>{
  await assertFails(send(db('bob')));
  await assertFails(updateDoc(doc(db('alice'),'conversations/alice_bob'),{participants:['alice','mallory']}));
});
test('existing messages are immutable and cannot be deleted',async()=>{
  await assertSucceeds(send(db('alice')));
  await assertFails(updateDoc(doc(db('alice'),'conversations/alice_bob/messages/m1'),{text:'edited'}));
  await assertFails(deleteDoc(doc(db('alice'),'conversations/alice_bob/messages/m1')));
});
test('oversized text and invalid location are rejected',async()=>{
  await assertFails(send(db('alice'),'oversize',{text:'x'.repeat(4001)}));
  await assertFails(setDoc(doc(db('alice'),'conversations/alice_bob/messages/loc'),{senderId:'alice',text:'',timestamp:2,type:'location',latitude:91,longitude:0}));
});
test('image and media must be created atomically and protected from third parties',async()=>{
  const database=db('alice');
  await assertFails(setDoc(doc(database,'conversations/alice_bob/messages/photo'),{senderId:'alice',text:'',timestamp:3,type:'image'}));
  const batch=writeBatch(database);
  batch.set(doc(database,'conversations/alice_bob/messages/photo'),{senderId:'alice',text:'',timestamp:3,type:'image'});
  batch.set(doc(database,'conversations/alice_bob/media/photo'),{senderId:'alice',mimeType:'image/jpeg',base64:'/9j/test'});
  await assertSucceeds(batch.commit());
  await assertSucceeds(getDoc(doc(db('bob'),'conversations/alice_bob/media/photo')));
  await assertFails(getDoc(doc(db('mallory'),'conversations/alice_bob/media/photo')));
});
test('hidden markers and tokens are owned by one user',async()=>{
  await assertSucceeds(setDoc(doc(db('alice'),'users/alice/hiddenChats/alice_bob'),{hiddenMessageId:''}));
  await assertFails(setDoc(doc(db('bob'),'users/alice/hiddenChats/alice_bob'),{hiddenMessageId:''}));
  await assertFails(getDocs(collection(db('bob'),'users/alice/devices')));
});
test('profiles cannot be overwritten by another account',async()=>{
  await assertSucceeds(updateDoc(doc(db('alice'),'users/alice'),{name:'Alice Updated'}));
  await assertFails(updateDoc(doc(db('bob'),'users/alice'),{name:'Spoof'}));
});
test('conversation creation preserves participant authorization',async()=>{
  const data={participants:['alice','mallory'],lastMessage:'',lastMessageId:'',lastMessageTime:1};
  await assertSucceeds(setDoc(doc(db('alice'),'conversations/alice_mallory'),data));
  await assertFails(setDoc(doc(db('bob'),'conversations/bob_invalid'),data));
  await assertSucceeds(getDoc(doc(db('alice'),'conversations/missing')));
});

test('clients cannot forge Supabase media references; verified backend records retain participant-only reads',async()=>{
  const path='conversations/alice_bob/messages/supabase';
  const record={senderId:'alice',text:'',type:'image',timestamp:2,storageProvider:'supabase',mediaPath:'alice_bob/supabase',mediaSize:200000,mediaSha256:'a'.repeat(64),mimeType:'image/png'};
  await assertFails(setDoc(doc(db('alice'),path),record));
  await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),path),record));
  await assertSucceeds(getDoc(doc(db('bob'),path)));
  await assertFails(getDoc(doc(db('mallory'),path)));
});

// Audio/video creation remains backend-only, never a client-writable storage reference.
test('video and audio records reject forged clients and preserve private participant reads',async()=>{
 for(const type of ['video','audio']){
  const path='conversations/alice_bob/messages/'+type;
  const record={senderId:'alice',text:'',type,timestamp:2,storageProvider:'supabase',mediaPath:'alice_bob/'+type,mediaSize:50000000,mediaSha256:'b'.repeat(64),mimeType:type==='video'?'video/mp4':'audio/wav',mediaName:'Class media'};
  await assertFails(setDoc(doc(db('alice'),path),record));
  await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),path),record));
  await assertSucceeds(getDoc(doc(db('bob'),path)));
  await assertFails(getDoc(doc(db('mallory'),path)));
 }
});

test('FCM tokens remain owner-only and token rotation can remove only the owner record',async()=>{
 const token={token:'fixture-device-token-123456789',updatedAt:serverTimestamp()};
 const path='users/alice/devices/'+'a'.repeat(64);
 await assertSucceeds(setDoc(doc(db('alice'),path),token));
 await assertFails(getDoc(doc(db('bob'),path)));
 await assertFails(setDoc(doc(db('bob'),path),token));
 await assertFails(deleteDoc(doc(db('bob'),path)));
 await assertSucceeds(deleteDoc(doc(db('alice'),path)));
});
test('expiring self-test push records are server-created and only their owner can read them',async()=>{
 const path='users/alice/pushTests/fixture';const record={kind:'test',owner:'alice',expiresAt:1};
 await assertFails(setDoc(doc(db('alice'),path),record));
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),path),record));
 await assertSucceeds(getDoc(doc(db('alice'),path)));
 await assertFails(getDoc(doc(db('bob'),path)));
});
