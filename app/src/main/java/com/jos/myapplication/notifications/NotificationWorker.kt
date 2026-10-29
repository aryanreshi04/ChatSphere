package com.jos.myapplication.notifications

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException

class NotificationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val target = NotificationTarget.from(listOf("recipientId", "conversationId", "messageId","kind").associateWith { inputData.getString(it).orEmpty() })
            ?: return Result.failure()
        return try { ChatNotifications.receive(applicationContext, target); Result.success() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
    companion object {
        fun enqueue(context: Context, target: NotificationTarget,expedited:Boolean=false) {
            val data = workDataOf("recipientId" to target.owner, "conversationId" to target.conversation, "messageId" to target.message,"kind" to target.kind)
            val builder=OneTimeWorkRequestBuilder<NotificationWorker>().setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,java.util.concurrent.TimeUnit.SECONDS)
            if(expedited&&android.os.Build.VERSION.SDK_INT>=31)builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            val work=builder.build()
            WorkManager.getInstance(context).enqueueUniqueWork("notify-${target.owner}-${target.kind}-${target.conversation}-${target.message}", ExistingWorkPolicy.KEEP, work)
        }
    }
}
