import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash,randomUUID} from 'node:crypto';
import {once} from 'node:events';
import {createReadStream} from 'node:fs';
import {mkdtemp,open,rm,stat,copyFile,access} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createAssistantServer} from '../server.mjs';
import {createMediaService,supabaseStorage,mediaLimit} from '../media.mjs';
import {hashStream,spoolMedia,validateMediaFile} from '../media-files.mjs';
import {Store} from '../store.mjs';

const atom=(name,body)=>{const out=Buffer.alloc(8+body.length);out.writeUInt32BE(out.length);out.write(name,4);body.copy(out,8);return out;};
function mp4(track){const handler=Buffer.alloc(24);handler.write(track,8);return Buffer.concat([
 atom('ftyp',Buffer.from('isom'+String.fromCharCode(0,0,0,0)+'isommp42')),
 atom('moov',atom('trak',atom('mdia',atom('hdlr',handler)))),atom('mdat',Buffer.alloc(32))]);}
async function source(t,size=10000,kind='wav'){
 const directory=await mkdtemp(join(tmpdir(),'chatsphere-test-'));t.after(()=>rm(directory,{recursive:true,force:true}));
 const file=join(directory,randomUUID());const handle=await open(file,'wx');
 try{
  if(kind==='wav'){
   const header=Buffer.alloc(44);header.write('RIFF');header.writeUInt32LE(size-8,4);header.write('WAVEfmt ',8);header.writeUInt32LE(16,16);
   header.writeUInt16LE(1,20);header.writeUInt16LE(1,22);header.writeUInt32LE(8000,24);header.writeUInt32LE(16000,28);header.writeUInt16LE(2,32);header.writeUInt16LE(16,34);header.write('data',36);header.writeUInt32LE(size-44,40);
   await handle.write(header);await handle.truncate(size);
  }else if(kind==='video'||kind==='m4a'){await handle.write(mp4(kind==='video'?'vide':'soun'));}
  else if(kind==='mp3'){const bytes=Buffer.alloc(417*3);for(let n=0;n<3;n++)Buffer.from([255,251,144,0]).copy(bytes,n*417);await handle.write(bytes);}
 }finally{await handle.close();}
 const info=await hashStream(createReadStream(file),50_000_001);
 return {file,...info,mime:kind==='wav'?'audio/wav':kind==='video'?'video/mp4':kind==='m4a'?'audio/mp4':'audio/mpeg'};
}
async function fixture(t,{failCommit=false}={}){
 const directory=await mkdtemp(join(tmpdir(),'chatsphere-storage-test-'));t.after(()=>rm(directory,{recursive:true,force:true}));
 const objects=new Map(),messages=new Map(),spools=[];let commits=0,rejectCommit=failCommit;
 const repository={thread:async()=>({participants:['alice','bob']}),message:async(c,id)=>messages.get(c+'/'+id),commit:async(c,id,msg,check)=>{
  if(rejectCommit){rejectCommit=false;throw Error('fixture commit failure');}
  if(messages.has(c+'/'+id))check(messages.get(c+'/'+id));else{messages.set(c+'/'+id,msg);commits++;}
 }};
 const storage={putOnce:async(path,item,mime)=>{
  assert.equal(Buffer.isBuffer(item),false);assert.equal((await stat(item.file)).size,item.size);spools.push(item.file);
  if(objects.has(path))assert.deepEqual(await hashStream(createReadStream(objects.get(path)),item.size),{size:item.size,sha256:item.sha256});
  else{const target=join(directory,randomUUID());await copyFile(item.file,target);objects.set(path,target);}
 },signedDownload:async(path)=>'http://127.0.0.1:'+downloads.address().port+'/'+encodeURIComponent(path)};
 const {createServer}=await import('node:http');
 const downloads=createServer((request,response)=>{const file=objects.get(decodeURIComponent(request.url.slice(1)));if(!file){response.writeHead(404);response.end();return;}createReadStream(file).pipe(response);});
 downloads.listen(0,'127.0.0.1');await once(downloads,'listening');
 const store=new Store(':memory:');
 const server=createAssistantServer({verifyToken:async token=>{if(token==='z'.repeat(24))throw Error('invalid');return {uid:token[0]==='a'?'alice':token[0]==='b'?'bob':'mallory'};},generate:async()=>'TEST FIXTURE',store,
  media:createMediaService({storage,repository,maxBytes:mediaLimit()})});
 server.listen(0,'127.0.0.1');await once(server,'listening');
 t.after(()=>{server.closeAllConnections();server.close();downloads.closeAllConnections();downloads.close();store.close();});
 const base='http://127.0.0.1:'+server.address().port;
 async function upload(item,id,token='a',headers={}){
  return fetch(base+'/v1/media/upload/alice_bob/'+id,{method:'POST',headers:{authorization:'Bearer '+token.repeat(24),'content-type':item.mime,'content-length':String(item.size),'x-media-sha256':item.sha256,'x-message-timestamp':String(Date.now()),...headers},body:createReadStream(item.file),duplex:'half'});
 }
 const download=(id,token='b')=>fetch(base+'/v1/media/download',{method:'POST',headers:{authorization:'Bearer '+token.repeat(24),'content-type':'application/json'},body:JSON.stringify({conversationId:'alice_bob',messageId:id})});
 return {upload,download,objects,messages,spools,commits:()=>commits};
}

test('HTTP disk-streamed media accepts exactly 50,000,000 bytes and retrieves every byte; rejects above ceiling',async t=>{
 const f=await fixture(t),at=await source(t,50_000_000),above=await source(t,50_000_001);
 const response=await f.upload(at,'boundary', 'a',{'x-media-name':encodeURIComponent('Class recording.wav')});assert.equal(response.status,200);
 assert.equal((await response.json()).media.mediaName,'Class recording.wav');
 const signed=await f.download('boundary');assert.equal(signed.status,200);
 const authorization=await signed.json();assert.equal(authorization.size,50_000_000);
 const retrieved=await fetch(authorization.url);assert.deepEqual(await hashStream(retrieved.body,50_000_000),{size:at.size,sha256:at.sha256});
 const rejected=await f.upload(above,'above');assert.equal(rejected.status,413);assert.equal(f.messages.size,1);
 for(const file of f.spools)await assert.rejects(()=>access(file));
});

test('video, M4A, MP3 and WAV upload/download retain types and private authorization',async t=>{
 const f=await fixture(t);
 for(const kind of ['video','m4a','mp3','wav']){
  const item=await source(t,200000,kind);const response=await f.upload(item,kind);assert.equal(response.status,200);
  const message=(await response.json()).media;assert.equal(message.type,kind==='video'?'video':'audio');assert.equal(message.mimeType,item.mime);
  const auth=await f.download(kind);assert.equal(auth.status,200);
  const retrieved=await fetch((await auth.json()).url);assert.deepEqual(await hashStream(retrieved.body,item.size),{size:item.size,sha256:item.sha256});
 }
 const item=await source(t);
 assert.equal((await f.upload(item,'intruder','m')).status,403);
 assert.equal((await f.upload(item,'unsigned','z')).status,401);
 assert.equal((await f.download('video','m')).status,403);
 f.messages.get('alice_bob/video').mediaPath='another/thread';assert.equal((await f.download('video')).status,404);
});

test('audio/video retries after ambiguous commit keep one immutable object and message',async t=>{
 for(const kind of ['video','wav']){
  const f=await fixture(t,{failCommit:true}),item=await source(t,10000,kind);
  assert.equal((await f.upload(item,'retry')).status,503);assert.equal(f.objects.size,1);
  assert.equal((await f.upload(item,'retry')).status,200);assert.equal((await f.upload(item,'retry')).status,200);
  assert.equal(f.objects.size,1);assert.equal(f.commits(),1);
  const other=await source(t,20000,'wav');assert.equal((await f.upload(other,'retry')).status,409);
  for(const path of f.spools)await assert.rejects(()=>access(path));
 }
});

test('server rejects mismatched media tracks, disguised containers, unsafe names and invalid duration',async t=>{
 const f=await fixture(t),video=await source(t,0,'video'),audio=await source(t,0,'m4a');
 assert.equal((await f.upload(video,'fakeaudio','a',{'content-type':'audio/mp4'})).status,415);
 assert.equal((await f.upload(audio,'fakevideo','a',{'content-type':'video/mp4'})).status,415);
 assert.equal((await f.upload(audio,'name','a',{'x-media-name':encodeURIComponent('../private')})).status,400);
 assert.equal((await f.upload(audio,'duration','a',{'x-media-duration-ms':'-1'})).status,400);
 const wav=await source(t);await (await open(wav.file,'r+')).close();
 assert.equal((await f.upload(wav,'wrongmime','a',{'content-type':'video/mp4'})).status,415);
 assert.equal(f.messages.size,0);
});

test('temporary media files are cleaned after bad input and stream failures',async t=>{
 const item=await source(t),read=createReadStream(item.file);const spooled=await spoolMedia(read,50_000_000);
 await assert.rejects(()=>validateMediaFile({...spooled,prefix:Buffer.from('not video')},'video/mp4'));
 await spooled.dispose();await assert.rejects(()=>access(spooled.file));
 const {Readable}=await import('node:stream');
 await assert.rejects(()=>spoolMedia(Readable.from([Buffer.alloc(100)]),99),/file-size limit/);
});

test('Supabase adapter streams files with an exact content-length and verifies duplicate bytes without upsert',async t=>{
 const item=await source(t,1000000);let uploads=0;
 const storage=supabaseStorage({SUPABASE_URL:'https://fixture.supabase.co',SUPABASE_SERVICE_ROLE_KEY:'TEST-SECRET'},async(url,request)=>{
  if(url.includes('/bucket/'))return Response.json({public:false});
  if(url.includes('/authenticated/'))return new Response(createReadStream(item.file));
  assert.equal(request.headers['x-upsert'],'false');assert.equal(request.headers['content-length'],String(item.size));assert.equal(request.duplex,'half');
  assert.equal(Buffer.isBuffer(request.body),false);
  assert.deepEqual(await hashStream(request.body,item.size),{size:item.size,sha256:item.sha256});uploads++;
  return new Response('{}',{status:uploads===1?200:409});
 });
 await storage.putOnce('alice_bob/audio',item,'audio/wav');await storage.putOnce('alice_bob/audio',item,'audio/wav');assert.equal(uploads,2);
});
