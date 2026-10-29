package com.jos.myapplication
import com.jos.myapplication.ml.StudyAlerts
import com.jos.myapplication.notifications.NotificationTarget
import org.junit.Assert.*
import org.junit.Test
class StudyAlertsTest {
 @Test fun actualClassifierDistinguishesStudyFromMeetupAndUnknown(){
  val study=StudyAlerts.decide("Please submit the homework assignment before class deadline",true,100000,0)
  assertTrue(study.alert);assertEquals("Study",study.category);assertTrue(study.score!!>=0.80)
  assertFalse(StudyAlerts.decide("Lunch coffee meet at the cafe",true,100000,0).alert)
  assertFalse(StudyAlerts.decide("qwertyxyz",true,100000,0).alert)
 }
 @Test fun consentAndCooldownPreventStaticOrRepeatedStudyAlerts(){
  val text="exam revision assignment homework deadline"
  assertFalse(StudyAlerts.decide(text,false,100000,0).alert)
  assertFalse(StudyAlerts.decide(text,true,100000,99999).alert)
  assertTrue(StudyAlerts.decide(text,true,100000,0).alert)
 }
 @Test fun testAndMessageNotificationKindsCannotBeInjected(){
  val values=mapOf("recipientId" to "alice","conversationId" to "notification-test","messageId" to "one","kind" to "test")
  assertEquals("test",NotificationTarget.from(values)?.kind)
  assertNull(NotificationTarget.from(values+("kind" to "admin")))
  assertEquals("message",NotificationTarget.from(values-"kind")?.kind)
 }
}
