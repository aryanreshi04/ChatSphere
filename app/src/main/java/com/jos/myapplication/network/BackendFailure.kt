package com.jos.myapplication.network
class BackendFailure(val status:Int,val code:String,val retryable:Boolean,val retryAfterMillis:Long?,message:String):IllegalStateException(message)
