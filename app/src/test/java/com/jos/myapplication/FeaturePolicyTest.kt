package com.jos.myapplication

import com.jos.myapplication.data.MessagePolicy
import com.jos.myapplication.ml.MessageCategories
import com.jos.myapplication.notifications.NotificationTarget
import org.junit.Assert.*
import org.junit.Test

class FeaturePolicyTest {
    @Test fun conversationIdentityIsIndependentOfWhoStartsTheChat() {
        assertEquals(MessagePolicy.conversationId("alice", "bob"), MessagePolicy.conversationId("bob", "alice"))
        assertThrows(IllegalArgumentException::class.java) { MessagePolicy.conversationId("alice", "alice") }
        assertThrows(IllegalArgumentException::class.java) { MessagePolicy.conversationId("alice", "path/injection") }
    }
    @Test fun locationValidationRejectsNonFiniteAndOutOfRangeCoordinates() {
        assertTrue(MessagePolicy.validLocation(-90.0, 180.0))
        assertFalse(MessagePolicy.validLocation(Double.NaN, 0.0))
        assertFalse(MessagePolicy.validLocation(0.0, Double.POSITIVE_INFINITY))
        assertFalse(MessagePolicy.validLocation(91.0, 0.0))
    }
    @Test fun notificationRoutingRejectsMissingOrInjectedDocumentPaths() {
        assertNull(NotificationTarget.from(mapOf("recipientId" to "alice", "conversationId" to "../../users", "messageId" to "m1")))
        assertNull(NotificationTarget.from(emptyMap()))
        assertEquals("alice_bob", NotificationTarget.from(mapOf("recipientId" to "alice", "conversationId" to "alice_bob", "messageId" to "m1"))?.conversation)
    }
    @Test fun classifierAbstainsOnUnsupportedInputAndUsesItsTrainingCounts() {
        val model = MessageCategories()
        assertNull(model.classify("")); assertNull(model.classify("你好")); assertNull(model.classify("qwertyxyz"))
        assertEquals("Study", model.classify("Please submit the laboratory assignment"))
        assertEquals("Meetup", model.classify("Lunch at the cafe then a walk"))
        // This is an inference smoke test, not an estimate of model accuracy.
    }
}
