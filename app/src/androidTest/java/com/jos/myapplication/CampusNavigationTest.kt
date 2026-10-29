package com.jos.myapplication

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jos.myapplication.location.CampusPlaces
import com.jos.myapplication.screens.maps.MapScreen
import com.jos.myapplication.ui.theme.ChatSphereTheme
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class CampusNavigationTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun destinationsPersistWithValidatedCoordinatesAndPreserveExistingEntries(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="campus-test-"+UUID.randomUUID();val store=CampusPlaces(context,name)
  try{
   val empty=JSONObject().put("name","No campus supplied")
   val first=store.add(empty,"Synthetic test group","Synthetic gate",12.34,56.78)
   val second=store.add(first,"Synthetic test group","Synthetic lab",12.35,56.79)
   val loaded=store.load(empty);assertEquals(2,loaded.getJSONArray("destinations").length())
   assertEquals("Synthetic gate",loaded.getJSONArray("destinations").getJSONObject(0).getString("name"))
   assertTrue(loaded.getJSONArray("destinations").getJSONObject(0).getBoolean("verified"))
   val gate=loaded.getJSONArray("destinations").getJSONObject(0)
   val edited=store.add(loaded,"Synthetic test group","Renamed gate",12.36,56.80,gate.getString("id"))
   assertEquals(2,edited.getJSONArray("destinations").length())
   assertEquals("Synthetic lab",edited.getJSONArray("destinations").getJSONObject(1).getString("name"))
   assertEquals(gate.getString("id"),edited.getJSONArray("destinations").getJSONObject(0).getString("id"))
   assertThrows(IllegalArgumentException::class.java){store.add(second,"Synthetic test group","Invalid",91.0,0.0)}
   assertThrows(IllegalArgumentException::class.java){store.add(second,"Synthetic test group","Synthetic gate",0.0,0.0)}
  }finally{context.getSharedPreferences(name,0).edit().clear().commit()}
 }
 @Test fun configuredDestinationSelectionOffersWalkingDirectionsWithoutApiKey(){
  assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS&&!BuildConfig.GOOGLE_MAPS_CONFIGURED)
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  val prefs=context.getSharedPreferences("chatsphere_campus",0);val before=prefs.getString("configuration",null)
  val monitor=instrumentation.addMonitor(IntentFilter(Intent.ACTION_VIEW).apply{addDataScheme("https")},Instrumentation.ActivityResult(Activity.RESULT_OK,null),true)
  try{
   val configuration=JSONObject("{\"name\":\"Synthetic test group\",\"destinations\":[{\"id\":\"fixture\",\"name\":\"Synthetic gate\",\"latitude\":12.34,\"longitude\":56.78,\"verified\":true}]}")
   prefs.edit().putString("configuration",configuration.toString()).commit()
   compose.runOnUiThread{compose.activity.setContent{ChatSphereTheme{MapScreen{}}}}
   compose.waitUntil(10000){compose.onAllNodes(hasText("Choose destination")).fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Choose destination").performScrollTo().performClick()
   compose.onNodeWithText("Synthetic gate").performClick()
   compose.onNodeWithText("Route: walking").assertExists()
   compose.onNodeWithText("Directions").performScrollTo().performClick()
   assertTrue("Google Maps/browser directions intent must be emitted",monitor.hits>0)
  }finally{
   instrumentation.removeMonitor(monitor)
   compose.runOnUiThread{compose.activity.setContent{}}
   if(before==null)prefs.edit().remove("configuration").commit()else prefs.edit().putString("configuration",before).commit()
  }
 }
}
