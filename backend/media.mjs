import {createHash} from 'node:crypto';
import {MEDIA_MIMES,mediaKind,imageMime,spoolMedia,validateMediaFile,hashStream,fileStream} from './media-files.mjs';
export {imageMime} from './media-files.mjs';
import {ApiError,RateLimiter} from './core.mjs';
export const MAX_FREE_FILE_BYTES=50_000_000;
export function mediaLimit(value='50000000') {
  const n=Number(value);
  if(!Number.isSafeInteger(n)||n<1||n>MAX_FREE_FILE_BYTES) throw new ApiError(503,'MEDIA_MAX_BYTES must be between 1 and 50000000.');
  return n;
}
export function mediaIds(conversation,id) {
  if(!/^[A-Za-z0-9_-]{1,260}$/.test(conversation||'')||!/^[A-Za-z0-9-]{1,80}$/.test(id||''))throw new ApiError(400,'Invalid conversation or message ID.');
  return conversation+'/'+id;
}
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
async function boundedBody(stream,limit,signal) {
  const chunks=[];let size=0;
  for await(const chunk of stream){signal?.throwIfAborted();size+=chunk.length;if(size>limit)throw new ApiError(413,'Request exceeds the configured size limit.');chunks.push(Buffer.from(chunk));}
  if(!size)throw new ApiError(400,'The request body is empty.');
  return Buffer.concat(chunks);
}
export function createMediaService({storage,repository,maxBytes=50_000_000}) {
  maxBytes=mediaLimit(maxBytes);const limiter=new RateLimiter();let active=0;
  async function member(uid,conversation){
    const thread=await repository.thread(conversation);
    if(!thread||!Array.isArray(thread.participants)||thread.participants.length!==2||!thread.participants.includes(uid))throw new ApiError(403,'You no longer have access to this conversation.');
    return thread;
  }
  function metadata(existing,uid,hash,size,mime,path){
    if(existing.type!==mediaKind(mime)||existing.senderId!==uid||existing.storageProvider!=='supabase'||existing.mediaPath!==path||existing.mediaSha256!==hash||existing.mediaSize!==size||existing.mimeType!==mime)throw new ApiError(409,'Message ID already belongs to different media. The existing message was preserved.');
    return existing;
  }
  return {
    async handle(request,identity,response){
      const abort=new AbortController();
      const disconnected=()=>{if(!response?.writableEnded)abort.abort();};
      request.once('aborted',disconnected);response?.once('close',disconnected);
      try {
        if(request.url==='/v1/media/download'&&request.method==='POST'){
          if(!request.headers['content-type']?.startsWith('application/json'))throw new ApiError(400,'JSON required.');
          let body;try{body=JSON.parse((await boundedBody(request,2048,abort.signal)).toString('utf8'));}catch(e){if(e instanceof ApiError)throw e;throw new ApiError(400,'Invalid media request.');}
          if(!body||Array.isArray(body)||Object.keys(body).some(k=>!['conversationId','messageId'].includes(k)))throw new ApiError(400,'Invalid media request.');
          const path=mediaIds(body.conversationId,body.messageId);
          await member(identity.uid,body.conversationId);
          const message=await repository.message(body.conversationId,body.messageId);
          if(!message||!['image','video','audio'].includes(message.type)||message.storageProvider!=='supabase'||message.mediaPath!==path||!Number.isSafeInteger(message.mediaSize)||message.mediaSize<1||message.mediaSize>MAX_FREE_FILE_BYTES||!/^[a-f0-9]{64}$/.test(message.mediaSha256||'')||!MEDIA_MIMES.includes(message.mimeType)||mediaKind(message.mimeType)!==message.type)throw new ApiError(404,'Uploaded media is unavailable.');
          return {url:await storage.signedDownload(path,60,abort.signal),expiresIn:60,mimeType:message.mimeType,size:message.mediaSize,sha256:message.mediaSha256};
        }
        const route=/^\/v1\/media\/upload\/([^/]+)\/([^/]+)$/.exec(request.url);
        if(request.method!=='POST'||!route)throw new ApiError(404,'Not found');
        const conversation=decodeURIComponent(route[1]),id=decodeURIComponent(route[2]),path=mediaIds(conversation,id);
        await member(identity.uid,conversation);
        const size=Number(request.headers['content-length']),timestamp=Number(request.headers['x-message-timestamp']);
        const hash=request.headers['x-media-sha256'],mime=request.headers['content-type'];
        if(!Number.isSafeInteger(size)||size<1||size>maxBytes)throw new ApiError(413,'Media exceeds the backend file-size limit ('+maxBytes+' bytes).');
        if(!Number.isSafeInteger(timestamp)||timestamp<1||timestamp>Date.now()+300_000||!/^[a-f0-9]{64}$/.test(hash||'')||!MEDIA_MIMES.includes(mime))throw new ApiError(400,'Invalid media metadata.');
        const type=mediaKind(mime);
        let mediaName;
        if(request.headers['x-media-name']){
          try{mediaName=decodeURIComponent(request.headers['x-media-name']);}catch{throw new ApiError(400,'Invalid attachment name.');}
          if(!mediaName.trim()||mediaName.length>120||/[\x00-\x1f\x7f/\\]/.test(mediaName))throw new ApiError(400,'Invalid attachment name.');
        }
        const durationMs=request.headers['x-media-duration-ms']===undefined?undefined:Number(request.headers['x-media-duration-ms']);
        if(durationMs!==undefined&&(!Number.isSafeInteger(durationMs)||durationMs<1||durationMs>86_400_000))throw new ApiError(400,'Invalid media duration.');
        const existing=await repository.message(conversation,id);
        if(existing){request.resume();return {media:metadata(existing,identity.uid,hash,size,mime,path)};}
        limiter.take(identity.uid);
        if(active>=2)throw new ApiError(429,'Media service is busy. Retry shortly.');
        active++;
        try {
          const source=await spoolMedia(request,maxBytes,abort.signal);
          try{
            if(source.size!==size||source.sha256!==hash)throw new ApiError(415,'Media bytes do not match the declared size or checksum.');
            await validateMediaFile(source,mime);
            abort.signal.throwIfAborted();
            await storage.putOnce(path,source,mime,abort.signal);
            await member(identity.uid,conversation);
            abort.signal.throwIfAborted();
            const message={senderId:identity.uid,text:'',type,timestamp,storageProvider:'supabase',mediaPath:path,mediaSha256:hash,mediaSize:size,mimeType:mime};
            if(mediaName)message.mediaName=mediaName;
            if(durationMs!==undefined)message.durationMs=durationMs;
            await repository.commit(conversation,id,message,previous=>metadata(previous,identity.uid,hash,size,mime,path));
            return {media:message};
          }finally{await source.dispose();}
        } finally {active--;}
      } finally {request.removeListener('aborted',disconnected);response?.removeListener('close',disconnected);}
    }
  };
}
export function supabaseStorage(env=process.env,fetcher=fetch) {
  const project=env.SUPABASE_URL,key=env.SUPABASE_SERVICE_ROLE_KEY,bucket=env.SUPABASE_MEDIA_BUCKET||'chat-media';
  if(!project||!key)throw new ApiError(503,'Supabase Storage is not configured on the backend. Follow docs/SETUP.md.');
  const base=new URL(project);
  if(base.protocol!=='https:'||base.username||base.password||base.pathname!=='/'||base.search||base.hash||! /^[a-z0-9-]{1,63}$/.test(bucket))throw new ApiError(503,'Invalid Supabase backend configuration.');
  const root=base.origin+'/storage/v1';
  const headers={apikey:key,authorization:'Bearer '+key};
  const objectPath=path=>'/'+bucket+'/'+path.split('/').map(encodeURIComponent).join('/');
  const signalFor=signal=>signal?AbortSignal.any([signal,AbortSignal.timeout(600_000)]):AbortSignal.timeout(600_000);
  async function privateBucket(signal){
    const response=await fetcher(root+"/bucket/"+bucket,{headers,redirect:"error",signal:signalFor(signal)});
    if(!response.ok)throw new ApiError(503,"Cannot verify Supabase bucket configuration. Check the project and backend service-role key.");
    const configuration=await response.json();
    if(configuration.public!==false)throw new ApiError(503,"The media bucket must be private. Turn off Public bucket in Supabase.");
  }
  return {
    async putOnce(path,source,mime,signal){
      // Buffer support is retained for old adapter callers/tests; HTTP uploads always supply a file.
      const buffered=Buffer.isBuffer(source);
      const size=buffered?source.length:source.size,sha256=buffered?digest(source):source.sha256;
      await privateBucket(signal);
      const result=await fetcher(root+'/object'+objectPath(path),{method:'POST',redirect:'error',headers:{...headers,'content-type':mime,'content-length':String(size),'x-upsert':'false'},body:buffered?source:fileStream(source),duplex:'half',signal:signalFor(signal)});
      if(result.ok){await result.body?.cancel();return;}
      let detail;try{detail=await result.json();}catch{}
      if(result.status===409||detail?.error==='Duplicate'||String(detail?.statusCode)==='409'){
        const prior=await fetcher(root+'/object/authenticated'+objectPath(path),{headers,redirect:'error',signal:signalFor(signal)});
        if(!prior.ok)throw new ApiError(503,'Cannot verify an earlier upload. Retry later.');
        const old=await hashStream(prior.body,size,signal);
        if(old.size!==size||old.sha256!==sha256)throw new ApiError(409,'An attachment with this message ID already exists. It was not overwritten.');
        return;
      }
      if(result.status===413)throw new ApiError(413,'Supabase rejected the file size. Check the bucket and global limit.');
      if(result.status===429)throw new ApiError(429,'Supabase quota or rate limit reached. Retry later.');
      throw new ApiError(503,'Supabase upload failed. Check project availability, private bucket, MIME restrictions and size limits.');
    },
    async signedDownload(path,seconds,signal){
      await privateBucket(signal);
      const result=await fetcher(root+'/object/sign'+objectPath(path),{method:'POST',redirect:'error',headers:{...headers,'content-type':'application/json'},body:JSON.stringify({expiresIn:seconds}),signal:signalFor(signal)});
      if(!result.ok)throw new ApiError(503,'Could not authorize the media download. Check Supabase availability and bucket configuration.');
      const data=await result.json();const value=data.signedURL||data.signedUrl;
      if(typeof value!=='string')throw new ApiError(503,'Invalid Storage download response.');
      const url=new URL(value.startsWith('https://')?value:(root+value));
      if(url.origin!==base.origin||!url.pathname.startsWith('/storage/v1/object/sign/'+bucket+'/'))throw new ApiError(503,'Invalid Storage download destination.');
      return url.toString();
    }
  };
}
export function firestoreMediaRepository(db,serverTimestamp) {
  const parent=id=>db.collection('conversations').doc(id);
  return {
    thread:async id=>(await parent(id).get()).data(),
    message:async(c,id)=>(await parent(c).collection('messages').doc(id).get()).data(),
    commit:async(c,id,message,check)=>db.runTransaction(async tx=>{
      const ref=parent(c),msg=ref.collection('messages').doc(id);
      const [thread,old]=await Promise.all([tx.get(ref),tx.get(msg)]);
      if(!thread.data()?.participants?.includes(message.senderId))throw new ApiError(403,'Conversation access changed.');
      if(old.exists){check(old.data());return;}
      tx.create(msg,{...message,serverTimestamp:serverTimestamp()});
      if(message.timestamp>=(thread.data().lastMessageTime||0))tx.update(ref,{lastMessage:message.type==='video'?'Video':message.type==='audio'?'Audio':'Photo',lastMessageTime:message.timestamp,lastMessageId:id});
    })
  };
}
