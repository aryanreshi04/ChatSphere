package com.jos.myapplication

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Generated synthetic fixtures: no private gallery files or network downloads. */
object AttachmentFixtures {
    fun directory(context:Context)=File(context.filesDir,"media-outbox/test-"+UUID.randomUUID()).apply{mkdirs()}
    fun wav(directory:File):File {
        val samples=32000;val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(samples*2+36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples*2)
        val file=File(directory,"test-audio.wav")
        file.outputStream().use{output->output.write(header.array());val chunk=ByteBuffer.allocate(3200).order(ByteOrder.LITTLE_ENDIAN)
            for(start in 0 until samples step 1600){chunk.clear();for(n in start until start+1600)chunk.putShort((kotlin.math.sin(n*2*Math.PI*440/16000)*4000).toInt().toShort());output.write(chunk.array())}}
        return file
    }
    fun video(directory:File):File {
        val file=File(directory,"test-video.mp4")
        val codec=MediaCodec.createEncoderByType("video/avc")
        val format=MediaFormat.createVideoFormat("video/avc",160,120).apply{
            setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE,128000);setInteger(MediaFormat.KEY_FRAME_RATE,10);setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
        }
        val muxer=MediaMuxer(file.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started=false;var track=-1;var frame=0;var inputDone=false;var outputDone=false
        try{
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start()
            val info=MediaCodec.BufferInfo();val deadline=System.nanoTime()+30_000_000_000L
            while(!outputDone){
                check(System.nanoTime()<deadline){"Synthetic video encoder timed out"}
                if(!inputDone){val index=codec.dequeueInputBuffer(10000)
                    if(index>=0){
                        if(frame<20){val bytes=ByteArray(160*120*3/2){if(it<160*120)(30+frame*8).toByte() else 128.toByte()}
                            codec.getInputBuffer(index)!!.apply{clear();put(bytes)};codec.queueInputBuffer(index,0,bytes.size,frame*100000L,0);frame++}
                        else{codec.queueInputBuffer(index,0,0,frame*100000L,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}
                    }
                }
                val out=codec.dequeueOutputBuffer(info,10000)
                if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=muxer.addTrack(codec.outputFormat);muxer.start();started=true}
                else if(out>=0){
                    val buffer=codec.getOutputBuffer(out)!!
                    if(info.size>0&&info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0){check(started);buffer.position(info.offset);buffer.limit(info.offset+info.size);muxer.writeSampleData(track,buffer,info)}
                    outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;codec.releaseOutputBuffer(out,false)
                }
            }
        }finally{runCatching{codec.stop()};codec.release();if(started)muxer.stop();muxer.release()}
        return file
    }
}
