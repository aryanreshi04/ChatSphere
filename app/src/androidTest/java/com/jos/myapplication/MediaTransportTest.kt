package com.jos.myapplication
import androidx.test.platform.app.InstrumentationRegistry
import com.jos.myapplication.network.BackendConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
class MediaTransportTest {
 private fun allowLocal(){
  assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS)
  if(android.os.Build.VERSION.SDK_INT>=37){
   val i=InstrumentationRegistry.getInstrumentation()
   i.uiAutomation.executeShellCommand("pm grant "+BuildConfig.APPLICATION_ID+" android.permission.ACCESS_LOCAL_NETWORK").use{java.io.FileInputStream(it.fileDescriptor).use{input->input.readBytes()}}
  }
 }
 @Test fun interruptedDownloadIsRejectedInsteadOfBeingAcceptedAsACompleteFile()=runBlocking{
  allowLocal()
  ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use{server->
   val worker=thread(isDaemon=true){
    server.accept().use{socket->
     val input=socket.getInputStream().bufferedReader()
     while(!input.readLine().isNullOrEmpty()){}
     socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\nConnection: close\r\n\r\nshort".toByteArray())
    }
   }
   BackendConnection.execute(Request.Builder().url("http://127.0.0.1:"+server.localPort+"/fixture").build()).use{response->
    try{withContext(Dispatchers.IO){response.body.bytes()};fail("Truncated download was accepted")}catch(_:java.io.IOException){}
   }
   worker.join(2000)
  }
 }
 @Test fun cancellingAnUploadCallClosesTheWaitingNetworkRequest()=runBlocking{
  allowLocal()
  val connected=CountDownLatch(1);val release=CountDownLatch(1)
  ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use{server->
   val worker=thread(isDaemon=true){server.accept().use{connected.countDown();release.await(5,TimeUnit.SECONDS)}}
   try{
    val job=async(Dispatchers.IO){BackendConnection.execute(Request.Builder().url("http://127.0.0.1:"+server.localPort+"/waiting").build()).close()}
    assertTrue(withContext(Dispatchers.IO){connected.await(3,TimeUnit.SECONDS)})
    withTimeout(3000){job.cancelAndJoin()}
    assertTrue(job.isCancelled)
   }finally{release.countDown();worker.join(2000)}
  }
 }
 @Test fun cancellingAnActiveDownloadClosesTheBodyWithoutWaitingForReadTimeout()=runBlocking{
  allowLocal()
  val reading=CountDownLatch(1);val release=CountDownLatch(1)
  ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use{server->
   val worker=thread(isDaemon=true){server.accept().use{socket->
    val input=socket.getInputStream().bufferedReader();while(!input.readLine().isNullOrEmpty()){}
    socket.getOutputStream().apply{write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\nConnection: close\r\n\r\nfirst".toByteArray());flush()}
    release.await(10,TimeUnit.SECONDS)
   }}
   try{
    val job=async(Dispatchers.IO){BackendConnection.consume(Request.Builder().url("http://127.0.0.1:"+server.localPort+"/body").build()){response->
     reading.countDown();response.body.byteStream().use{it.copyTo(object:java.io.OutputStream(){override fun write(value:Int){};override fun write(bytes:ByteArray,offset:Int,length:Int){}})}
    }}
    assertTrue(withContext(Dispatchers.IO){reading.await(3,TimeUnit.SECONDS)})
    withTimeout(3000){job.cancelAndJoin()};assertTrue(job.isCancelled)
   }finally{release.countDown();worker.join(2000)}
  }
 }

}
