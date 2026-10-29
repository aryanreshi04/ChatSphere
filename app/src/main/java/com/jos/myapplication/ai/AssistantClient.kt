package com.jos.myapplication.ai
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.jos.myapplication.network.BackendConnection
import com.jos.myapplication.network.BackendFailure
import com.jos.myapplication.network.LocalNetworkAccess
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
class AssistantClient(private val context:Context) {
    val configured get()=BackendConnection.base(context).isNotBlank()
    val usesLocalNetwork get()=LocalNetworkAccess.localUrl(BackendConnection.base(context))
    private val transport=BackendConnection.client.newBuilder().readTimeout(80,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS).build()
    suspend fun reply(history:JSONArray,requestId:String,onStatus:(String)->Unit={}):String {
        val owner=FirebaseAuth.getInstance().currentUser?.uid?:error("Sign in again to use the assistant")
        val messages=JSONArray()
        for(i in maxOf(0,history.length()-20) until history.length()){
            val m=history.getJSONObject(i)
            messages.put(JSONObject().put("role",m.getString("role")).put("content",m.getString("content").take(1000)))
        }
        val body=JSONObject().put("requestId",requestId).put("messages",messages)
        for(attempt in 0..1){
            check(FirebaseAuth.getInstance().currentUser?.uid==owner){"Account changed; reopen the assistant"}
            onStatus(if(attempt==0)"Waiting for an AI reply…"else"Retrying the interrupted connection…")
            try {
                val answer=BackendConnection.json(context,"/v1/chat",body,transport).getString("reply")
                check(FirebaseAuth.getInstance().currentUser?.uid==owner){"Account changed; reopen the assistant"}
                require(answer.isNotBlank()&&answer.length<=8000){"AI returned an invalid answer"}
                return answer
            }catch(e:BackendFailure){
                val wait=AssistantRetryPolicy.delay(attempt,e.status,e.code,e.retryable,e.retryAfterMillis,Math.random())?:throw e
                delay(wait)
            }catch(e:IOException){
                val wait=AssistantRetryPolicy.delay(attempt,null,null,true,null,Math.random())
                    ?:throw IOException(if(e is java.net.SocketTimeoutException||e.cause is java.net.SocketTimeoutException)"The AI connection timed out. Retry the same request."else"Could not reach the AI backend. Check the running backend, URL and USB/internet connection.")
                delay(wait)
            }
        }
        error("AI request could not finish. Retry the same request.")
    }
}
