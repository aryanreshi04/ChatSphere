package com.jos.myapplication.ai

/** At most one transport/busy retry. Provider retries are already bounded on the backend. */
object AssistantRetryPolicy {
    fun delay(attempt:Int,status:Int?,code:String?,retryable:Boolean,retryAfterMillis:Long?,jitter:Double):Long? {
        if(attempt!=0||!retryable)return null
        if(status!=null&&(status !in setOf(502,503,504)||code !in setOf("AI_BUSY","BACKEND_UNAVAILABLE")))return null
        val wait=maxOf(650+(jitter.coerceIn(0.0,1.0)*250).toLong(),retryAfterMillis?:0)
        return wait.takeIf{it<=3000}
    }
}
