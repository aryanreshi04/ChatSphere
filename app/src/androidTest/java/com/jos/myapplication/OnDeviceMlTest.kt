package com.jos.myapplication

import com.jos.myapplication.data.Message
import com.jos.myapplication.data.SyncState
import com.jos.myapplication.ml.SmartReplies
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OnDeviceMlTest {
    @Test fun bundledSmartReplyPerformsRealEnglishInference() = runBlocking {
        val result = SmartReplies.generate(listOf(Message("fixture", "peer", "Would you like to have lunch tomorrow?", System.currentTimeMillis(), state = SyncState.SENT)), "self")
        assertTrue("Actual model candidates must be retained; fallbacks alone do not verify ML", result.modelCount>0)
        assertEquals(3,result.replies.size)
    }
}
