package com.jos.myapplication.notifications

import android.content.Context
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.jos.myapplication.BuildConfig
import com.jos.myapplication.network.BackendConnection
import com.jos.myapplication.network.BackendFailure
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** A separate durable delivery request; notification failures never change a sent chat message. */
class OutgoingPushWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
 override suspend fun doWork():Result {
  val owner=inputData.getString("owner")?:return Result.failure()
  val conversation=inputData.getString("conversation")?:return Result.failure()
  val message=inputData.getString("message")?:return Result.failure()
  if(BuildConfig.USE_FIREBASE_EMULATORS||FirebaseAuth.getInstance().currentUser?.uid!=owner||BackendConnection.base(applicationContext).isBlank())return Result.success()
  return try{
   BackendConnection.json(applicationContext,"/v1/push/message",JSONObject().put("conversationId",conversation).put("messageId",message))
   Result.success()
  }catch(error:CancellationException){throw error}
  catch(error:Exception){
   val retry=error is java.io.IOException || error is BackendFailure&&error.status in setOf(429,500,502,503,504)
   if(retry&&runAttemptCount<4)Result.retry()else Result.failure()
  }
 }
 companion object {
  fun enqueue(context:Context,owner:String,conversation:String,message:String){
   if(BuildConfig.USE_FIREBASE_EMULATORS)return
   val request=OneTimeWorkRequestBuilder<OutgoingPushWorker>().setInputData(workDataOf("owner" to owner,"conversation" to conversation,"message" to message))
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,java.util.concurrent.TimeUnit.SECONDS).build()
   WorkManager.getInstance(context).enqueueUniqueWork("outgoing-push-$owner-$conversation-$message",ExistingWorkPolicy.KEEP,request)
  }
 }
}
