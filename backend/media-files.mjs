import {createHash} from 'node:crypto';
import {createReadStream,createWriteStream} from 'node:fs';
import {mkdtemp,rm,open} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {Transform} from 'node:stream';
import {pipeline} from 'node:stream/promises';
import {ApiError} from './core.mjs';

export const MEDIA_MIMES=['image/jpeg','image/png','image/webp','image/heic','image/heif','video/mp4','audio/mp4','audio/mpeg','audio/wav'];
export const mediaKind=mime=>mime.startsWith('image/')?'image':mime==='video/mp4'?'video':'audio';
export function imageMime(bytes) {
  if(bytes.length>=3&&bytes[0]===255&&bytes[1]===216&&bytes[2]===255)return 'image/jpeg';
  if(bytes.length>=8&&bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))return 'image/png';
  if(bytes.length>=12&&bytes.toString('ascii',0,4)==='RIFF'&&bytes.toString('ascii',8,12)==='WEBP')return 'image/webp';
  if(bytes.length>=16&&bytes.toString('ascii',4,8)==='ftyp'){
    const brands=bytes.toString('ascii',8,Math.min(bytes.length,64));
    if(/hei[cx]|hev[cx]/.test(brands))return 'image/heic';
    if(/mif1|msf1/.test(brands))return 'image/heif';
  }
  throw new ApiError(415,'Choose a real JPEG, PNG, WebP or HEIF image.');
}
// Bounded disk spool: never concatenate a video/audio request into a single Buffer.
export async function spoolMedia(stream,limit,signal){
  const directory=await mkdtemp(join(tmpdir(),'chatsphere-media-'));
  const file=join(directory,'upload');let size=0,prefix=Buffer.alloc(0);const hash=createHash('sha256');
  const dispose=()=>rm(directory,{recursive:true,force:true}); // Only this operation's freshly created temp directory.
  try{
    const counter=new Transform({transform(chunk,encoding,done){
      size+=chunk.length;
      if(size>limit)return done(new ApiError(413,'Media exceeds the configured file-size limit.'));
      hash.update(chunk);
      if(prefix.length<64)prefix=Buffer.concat([prefix,chunk.subarray(0,64-prefix.length)]);
      done(null,chunk);
    }});
    await pipeline(stream,counter,createWriteStream(file,{flags:'wx',mode:0o600}),{signal});
    if(!size)throw new ApiError(400,'The media file is empty.');
    return {file,size,prefix,sha256:hash.digest('hex'),dispose};
  }catch(error){await dispose();throw error;}
}
export async function hashStream(stream,limit,signal){
  let size=0;const hash=createHash('sha256');
  for await(const chunk of stream){signal?.throwIfAborted();size+=chunk.length;if(size>limit)throw new ApiError(409,'An attachment with this message ID already exists. It was not overwritten.');hash.update(chunk);}
  return {size,sha256:hash.digest('hex')};
}
export const fileStream=source=>createReadStream(source.file,{highWaterMark:64*1024});

export async function validateMediaFile(source,mime){
  if(!MEDIA_MIMES.includes(mime))throw new ApiError(415,'Unsupported media type.');
  if(mediaKind(mime)==='image'){
    if(imageMime(source.prefix)!==mime)throw new ApiError(415,'Image bytes do not match the declared type.');
    return;
  }
  const handle=await open(source.file,'r');
  const invalid=()=>new ApiError(415,'File content does not match a supported MP4 video, MP3, M4A or WAV audio file.');
  async function read(position,size){const bytes=Buffer.alloc(size);const result=await handle.read(bytes,0,size,position);if(result.bytesRead!==size)throw invalid();return bytes;}
  try{
    if(mime==='video/mp4'||mime==='audio/mp4'){
      if(source.prefix.toString('ascii',4,8)!=='ftyp')throw invalid();
      const ftypLength=source.prefix.readUInt32BE(0);
      if(ftypLength<16||ftypLength>source.size)throw invalid();
      const brands=source.prefix.toString('ascii',8,Math.min(ftypLength,source.prefix.length));
      if(!/isom|iso[2-9]|mp4[12]|avc1|M4[AVB] /.test(brands))throw invalid();
      let boxes=0;const tracks=new Set();
      async function walk(start,end,depth){
        let cursor=start;
        while(cursor<end){
          if(++boxes>10000||end-cursor<8)throw invalid();
          const header=await read(cursor,8);let length=header.readUInt32BE(0),offset=8;
          if(length===1){const extended=(await read(cursor+8,8)).readBigUInt64BE();if(extended>BigInt(source.size))throw invalid();length=Number(extended);offset=16;}
          if(length===0)length=end-cursor;
          if(length<offset||cursor+length>end)throw invalid();
          const type=header.toString('ascii',4,8);
          if(['moov','trak','mdia'].includes(type)&&depth<3)await walk(cursor+offset,cursor+length,depth+1);
          if(type==='hdlr'&&depth===3&&length>=offset+12)tracks.add((await read(cursor+offset+8,4)).toString('ascii'));
          cursor+=length;
        }
      }
      await walk(0,source.size,0);
      if(mime==='video/mp4'?!tracks.has('vide'):(!tracks.has('soun')||tracks.has('vide')))throw invalid();
    }else if(mime==='audio/wav'){
      const first=await read(0,12);
      if(first.toString('ascii',0,4)!=='RIFF'||first.toString('ascii',8,12)!=='WAVE'||first.readUInt32LE(4)+8!==source.size)throw invalid();
      let cursor=12,fmt=false,data=false,chunks=0;
      while(cursor+8<=source.size){
        if(++chunks>10000)throw invalid();
        const header=await read(cursor,8),length=header.readUInt32LE(4),name=header.toString('ascii',0,4);
        if(cursor+8+length>source.size)throw invalid();
        if(name==='fmt '){if(length<16)throw invalid();const format=await read(cursor+8,16);fmt=[1,3].includes(format.readUInt16LE(0))&&format.readUInt16LE(2)>0&&format.readUInt32LE(4)>0;}
        if(name==='data'&&length>0)data=true;
        cursor+=8+length+(length%2);
      }
      if(!fmt||!data||cursor!==source.size)throw invalid();
    }else if(mime==='audio/mpeg'){
      let start=0;
      if(source.prefix.toString('ascii',0,3)==='ID3'){
        const tag=await read(0,10);if(tag.subarray(6,10).some(n=>n>127))throw invalid();
        start=10+((tag[6]<<21)|(tag[7]<<14)|(tag[8]<<7)|tag[9])+((tag[5]&16)?10:0);
      }
      const frame=await read(start,4);
      const version=(frame[1]>>3)&3,bitrate=(frame[2]>>4)&15,sample=(frame[2]>>2)&3;
      if(frame[0]!==255||(frame[1]&0xe6)!==0xe2||version===1||bitrate===0||bitrate===15||sample===3)throw invalid();
      const kbps=(version===3?[0,32,40,48,56,64,80,96,112,128,160,192,224,256,320]:[0,8,16,24,32,40,48,56,64,80,96,112,128,144,160])[bitrate];
      const hz=[44100,48000,32000][sample]/(version===3?1:version===2?2:4);
      const length=Math.floor((version===3?144000:72000)*kbps/hz)+((frame[2]>>1)&1);
      if(start+length>source.size)throw invalid();
    }
  }finally{await handle.close();}
}
