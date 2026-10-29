package com.jos.myapplication
import com.jos.myapplication.ai.AssistantRetryPolicy
import org.junit.Assert.*
import org.junit.Test
class AssistantRetryPolicyTest {
 @Test fun networkAndBusyRetryOnlyOnceAndHonorSmallRetryAfter(){
  assertEquals(650L,AssistantRetryPolicy.delay(0,null,null,true,null,0.0))
  assertEquals(2000L,AssistantRetryPolicy.delay(0,503,"AI_BUSY",true,2000,0.0))
  assertNull(AssistantRetryPolicy.delay(1,null,null,true,null,0.0))
 }
 @Test fun permanentQuotaAuthAndAlreadyRetriedProviderFailuresDoNotMultiplyRequests(){
  for(code in listOf("AI_CONFIGURATION","AI_QUOTA_EXHAUSTED","AI_DAILY_LIMIT","AI_PROVIDER_UNAVAILABLE","AI_TIMEOUT"))assertNull(AssistantRetryPolicy.delay(0,503,code,true,null,0.0))
  assertNull(AssistantRetryPolicy.delay(0,401,"AUTHENTICATION_REQUIRED",false,null,0.0))
  assertNull(AssistantRetryPolicy.delay(0,429,"AI_RATE_LIMIT",true,1000,0.0))
  assertNull(AssistantRetryPolicy.delay(0,503,"AI_BUSY",true,60000,0.0))
 }
}
