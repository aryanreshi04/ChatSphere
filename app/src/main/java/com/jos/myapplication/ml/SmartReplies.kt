package com.jos.myapplication.ml

import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import com.jos.myapplication.data.Message
import com.jos.myapplication.data.SyncState
import com.jos.myapplication.network.awaitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object SmartReplies {
    suspend fun suggest(messages:List<Message>,owner:String):List<String> = generate(messages,owner).replies
    suspend fun generate(messages:List<Message>,owner:String):ReplySuggestions = withContext(Dispatchers.Default) {
        val last=messages.lastOrNull()
        if(last==null||last.type!="text"||last.senderId==owner||last.text.isBlank())return@withContext ReplySuggestions(emptyList(),0)
        val history=messages.takeLast(10).filter{it.type=="text"&&it.state in setOf(SyncState.SENT,SyncState.CACHED)}
        if(history.isEmpty())return@withContext ReplySuggestions(emptyList(),0)
        val client=SmartReply.getClient()
        val candidates=try {
            val result=withTimeout(3500){client.suggestReplies(history.map{
                if(it.senderId==owner)TextMessage.createForLocalUser(it.text,it.timestamp)
                else TextMessage.createForRemoteUser(it.text,it.timestamp,it.senderId)
            }).awaitResult()}
            if(result.status==SmartReplySuggestionResult.STATUS_SUCCESS)result.suggestions.map{it.text}else emptyList()
        } catch(_:TimeoutCancellationException){emptyList()}
        catch(e:CancellationException){throw e}
        catch(_:Exception){emptyList()}
        finally{client.close()}
        ReplyQuality.compose(last.text,history.dropLast(1).map{it.text},candidates)
    }
}
