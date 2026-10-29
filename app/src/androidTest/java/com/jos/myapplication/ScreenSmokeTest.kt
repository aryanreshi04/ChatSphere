package com.jos.myapplication

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jos.myapplication.screens.assistant.AssistantScreen
import com.jos.myapplication.screens.maps.MapScreen
import com.jos.myapplication.ui.theme.ChatSphereTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean

class ScreenSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun mapLoadsWithoutLocationPermissionOrApiKey() {
        assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS)
        compose.runOnUiThread { compose.activity.setContent { ChatSphereTheme { MapScreen {} } } }
        compose.onNodeWithText("Live map").assertIsDisplayed()
        compose.onNodeWithText("Use my location").assertIsDisplayed()
        compose.onNodeWithText("Following: on").performClick()
        compose.onNodeWithText("Following: off").assertIsDisplayed()
        val initialized = AtomicBoolean(false)
        fun findMap(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
            return null
        }
        compose.waitUntil(20_000) {
            compose.activity.runOnUiThread {
                findMap(compose.activity.window.decorView)?.evaluateJavascript(
                    "typeof window.renderMap === 'function' && document.getElementById('map').classList.contains('leaflet-container')") {
                    initialized.set(it == "true")
                }
            }
            initialized.get()
        }
    }
    @Test fun assistantReportsMissingBackendWithoutFabricatedReplies() {
        assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS)
        compose.runOnUiThread { compose.activity.setContent { ChatSphereTheme { AssistantScreen {} } } }
        compose.onNodeWithText("Student AI assistant").assertIsDisplayed()
        compose.onNodeWithText("AI backend is not configured. No generated replies are available. See docs/SETUP.md to connect a secure backend.").assertIsDisplayed()
    }
}
