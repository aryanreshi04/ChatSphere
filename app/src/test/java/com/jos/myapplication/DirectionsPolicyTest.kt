package com.jos.myapplication
import com.jos.myapplication.location.DirectionsPolicy
import org.junit.Assert.*
import org.junit.Test
class DirectionsPolicyTest {
 @Test fun walkingRouteUsesVerifiedCoordinatesAndOptionalOrigin(){
  val url=DirectionsPolicy.url(12.5,77.5,12.4 to 77.4)
  assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1"));assertTrue(url.contains("travelmode=walking"))
  assertTrue(url.contains("origin=12.4,77.4"));assertTrue(url.contains("destination=12.5,77.5"))
  assertFalse(DirectionsPolicy.url(0.0,0.0).contains("&origin="))
 }
 @Test fun routesRejectUnsupportedModesInvalidCoordinatesAndUnsafeNames(){
  assertThrows(IllegalArgumentException::class.java){DirectionsPolicy.url(Double.NaN,0.0)}
  assertThrows(IllegalArgumentException::class.java){DirectionsPolicy.url(0.0,181.0)}
  assertThrows(IllegalArgumentException::class.java){DirectionsPolicy.url(0.0,0.0,mode="javascript:bad")}
  assertFalse(DirectionsPolicy.validPlace("",0.0,0.0));assertFalse(DirectionsPolicy.validPlace("Gate\nInjected",0.0,0.0))
  assertTrue(DirectionsPolicy.validPlace("User supplied gate",0.0,0.0))
 }
}
