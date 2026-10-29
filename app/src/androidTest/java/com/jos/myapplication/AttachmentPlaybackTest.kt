package com.jos.myapplication

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.VideoView
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jos.myapplication.data.Message
import com.jos.myapplication.media.VideoPlaybackActivity
import com.jos.myapplication.screens.individualchat.MessageContent
import com.jos.myapplication.ui.theme.ChatSphereTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class AttachmentPlaybackTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun audioCardPlaysPausesAndKeepsTheSaveAction() {
        assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=AttachmentFixtures.directory(context);val audio=AttachmentFixtures.wav(directory)
        try{
            compose.runOnUiThread{compose.activity.setContent{ChatSphereTheme{
                MessageContent(Message("playback-test","fixture","",1,type="audio",mimeType="audio/wav",mediaName="test-audio.wav",mediaSize=audio.length(),localMediaPath=audio.path),"",null,false)
            }}}
            compose.waitUntil(10000){compose.onAllNodes(androidx.compose.ui.test.hasText("Play audio")).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Play audio").performClick()
            compose.onNodeWithText("Pause audio").assertIsDisplayed().performClick()
            compose.onNodeWithText("Play audio").assertIsDisplayed()
            compose.onNodeWithText("Save file").assertIsDisplayed()
        }finally{compose.runOnUiThread{compose.activity.setContent{}};directory.deleteRecursively()}
    }
    @Test fun fullScreenVideoDecodesAndReachesTheEndWithPrivateFileUri() {
        assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=AttachmentFixtures.directory(context);val video=AttachmentFixtures.video(directory)
        val uri=FileProvider.getUriForFile(context,context.packageName+".media",video)
        fun find(view:View):VideoView?{if(view is VideoView)return view;if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let{return it};return null}
        fun completed(view:View):Boolean{if(view is TextView&&view.text.toString().startsWith("Video finished."))return true;if(view is ViewGroup)for(i in 0 until view.childCount)if(completed(view.getChildAt(i)))return true;return false}
        try{
            ActivityScenario.launch<VideoPlaybackActivity>(Intent(context,VideoPlaybackActivity::class.java).setData(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use{scenario->
                val advanced=AtomicBoolean(false);val finished=AtomicBoolean(false)
                compose.waitUntil(15000){scenario.onActivity{activity->find(activity.window.decorView)?.let{advanced.set(it.currentPosition>100&&it.duration>0)}};advanced.get()}
                var checks=0
                compose.waitUntil(15000){scenario.onActivity{activity->find(activity.window.decorView)?.let{
                    if(checks++%100==0)println("VIDEO_TEST_STATE position="+it.currentPosition+" duration="+it.duration+" playing="+it.isPlaying+" completed="+completed(activity.window.decorView))
                    finished.set(it.duration>0&&!it.isPlaying&&completed(activity.window.decorView))
                }};finished.get()}
                assertTrue(advanced.get());assertTrue(finished.get())
            }
        }finally{directory.deleteRecursively()}
    }
}
