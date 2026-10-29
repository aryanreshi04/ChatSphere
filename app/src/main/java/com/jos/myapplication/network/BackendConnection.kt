package com.jos.myapplication.network

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.jos.myapplication.BuildConfig
import com.jos.myapplication.R
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object BackendConnection {
    val client = OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(70,TimeUnit.SECONDS)
        .writeTimeout(60,TimeUnit.SECONDS).callTimeout(180,TimeUnit.SECONDS).followRedirects(false)
        .followSslRedirects(false).retryOnConnectionFailure(false).build()
    fun base(context:Context):String {
        val saved=context.getSharedPreferences("backend_connection",0).getString("url",null)
        return saved ?: context.getString(R.string.assistant_endpoint).trim().removeSuffix("/v1/chat").trimEnd('/')
    }
    fun validate(value:String):String {
        val url=value.trim().trimEnd('/').toHttpUrlOrNull() ?: error("Enter a valid backend URL")
        require(url.username.isEmpty()&&url.password.isEmpty()&&url.query==null&&url.fragment==null&&url.encodedPath=="/") { "Use the backend base URL without a path, query or credentials" }
        require(url.isHttps || BuildConfig.DEBUG) { "Use HTTPS. Local HTTP is available only in debug builds." }
        return url.toString().trimEnd('/')
    }
    fun save(context:Context,value:String) {
        val normalized=if(value.isBlank()) "" else validate(value)
        context.getSharedPreferences("backend_connection",0).edit().putString("url",normalized).apply()
    }
    suspend fun authorized(context:Context,path:String):Request.Builder {
        val base=base(context)
        check(base.isNotBlank()) { "Connect the backend from Student AI assistant → Connect backend. See docs/SETUP.md." }
        validate(base)
        check(!LocalNetworkAccess.localUrl(base)||LocalNetworkAccess.granted(context)) { "Allow local network access (Nearby devices) to connect to your computer." }
        val user=FirebaseAuth.getInstance().currentUser ?: error("Please sign in first")
        val token=withTimeout(15_000){user.getIdToken(false).awaitResult().token} ?: error("Sign-in token unavailable")
        check(FirebaseAuth.getInstance().currentUser?.uid==user.uid){"Account changed; reopen this screen"}
        return Request.Builder().url(base+path).header("Authorization","Bearer "+token)
    }
    // Keep cancellation connected until the response body has been fully consumed.
    suspend fun <T> consume(request:Request,httpClient:OkHttpClient=client,block:suspend (Response)->T):T = kotlinx.coroutines.coroutineScope {
        val call=httpClient.newCall(request)
        val cancellation=launch(kotlinx.coroutines.Dispatchers.Unconfined,start=kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            try { kotlinx.coroutines.awaitCancellation() } finally { call.cancel() }
        }
        try { awaitCall(call).use { block(it) } }
        catch(error:Exception) {
            // Closing a stalled socket after Stop can throw IOException; preserve coroutine cancellation.
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            throw error
        } finally { cancellation.cancel() }
    }
    suspend fun execute(request:Request,httpClient:OkHttpClient=client):Response = awaitCall(httpClient.newCall(request))
    private suspend fun awaitCall(call:Call):Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(IOException("Could not reach the backend. Check that it is running and the URL/USB connection is correct.",e))}
            override fun onResponse(call:Call,response:Response){if(continuation.isActive)continuation.resumeWith(Result.success(response))else response.close()}
        })
    }
    fun readJson(response:Response):JSONObject {
        val bytes=response.body.byteStream().use { com.jos.myapplication.media.MediaPolicy.prefix(it,100_001) }
        check(bytes.size<=100_000) { "Backend response is too large" }
        val body=runCatching { JSONObject(String(bytes,Charsets.UTF_8)) }.getOrElse { error("Backend returned an invalid response") }
        if(!response.isSuccessful){
            val seconds=body.optLong("retryAfterSeconds",-1).takeIf{it>=0}
                ?:response.header("Retry-After")?.toLongOrNull()?.takeIf{it>=0}
            throw BackendFailure(response.code,body.optString("code","BACKEND_ERROR"),body.optBoolean("retryable",false),
                seconds?.coerceAtMost(604800)?.times(1000),body.optString("error").take(400).ifBlank{"Backend request failed ("+response.code+"). Retry later."})
        }
        return body
    }
    suspend fun json(context:Context,path:String,body:JSONObject,httpClient:OkHttpClient=client):JSONObject {
        val request=authorized(context,path).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){consume(request,httpClient) { readJson(it) }}
    }
}
