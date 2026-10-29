package com.jos.myapplication
import com.jos.myapplication.ml.ReplyQuality
import org.junit.Assert.*
import org.junit.Test
class ReplyQualityTest {
 @Test fun malformedAndDuplicateModelOutputsAreCleanedAndFilled(){
  val result=ReplyQuality.compose("Would you like lunch?",emptyList(),listOf("yes","Yes!","sure","", "x".repeat(200),"<script>"))
  assertEquals(3,result.replies.size);assertEquals(1,result.modelCount)
  assertTrue(result.replies.any{it.contains("can't")});assertTrue(result.replies.any{it.contains("time")})
  assertTrue(result.label.contains("fallback"))
 }
 @Test fun representativeContextsProduceThreeValidDiverseChoices(){
  for(text in listOf("Hello!","Thank you","Can we meet tomorrow?","Where is the lab?","Please send the assignment","I feel sad today","okay","你好")){
   val result=ReplyQuality.compose(text,emptyList(),emptyList());assertEquals(text,3,result.replies.size)
   assertTrue(result.replies.all{it.isNotBlank()&&it.length<=140})
   for(a in 0..2)for(b in a+1..2)assertTrue(text,ReplyQuality.distinct(result.replies[a],result.replies[b]))
  }
 }
 @Test fun emotionalRepliesDoNotReuseAnInappropriatePositiveCandidate(){
  val result=ReplyQuality.compose("I'm really upset",emptyList(),listOf("Great!","Congratulations!"))
  assertEquals(0,result.modelCount);assertTrue(result.replies.any{it.contains("listen")})
 }
 @Test fun precedingConversationInformsFallbackWithoutInventingATime(){
  val result=ReplyQuality.compose("Can we meet?",listOf("Let's aim for 4 pm"),emptyList())
  assertTrue(result.replies.any{it.contains("time we discussed")})
  assertTrue(ReplyQuality.compose("Can we meet?",emptyList(),emptyList()).replies.any{it.contains("What time")})
 }
 @Test fun commonParaphrasesAndIncompleteFragmentsDoNotFillAllThreeSlots(){
  assertFalse(ReplyQuality.distinct("No.","I can't make it this time."))
  assertFalse(ReplyQuality.distinct("Hi!","Hello!"))
  assertFalse(ReplyQuality.distinct("You're welcome!","No problem."))
  assertNull(ReplyQuality.normalize("and"));assertNull(ReplyQuality.normalize("I will go to"))
  assertEquals(3,ReplyQuality.compose("Thanks!",emptyList(),listOf("You're welcome","No problem","Anytime")).replies.size)
 }
 @Test fun emptyInputAndGrammarAreHandled(){
  assertTrue(ReplyQuality.compose("",emptyList(),listOf("yes")).replies.isEmpty())
  assertEquals("I'm good, thanks!",ReplyQuality.normalize("im good"))
  assertEquals("I don't know.",ReplyQuality.normalize("i dont know"))
  assertEquals("What time?",ReplyQuality.normalize("what time"))
  assertNull(ReplyQuality.normalize("https://example.test"))
 }
}
