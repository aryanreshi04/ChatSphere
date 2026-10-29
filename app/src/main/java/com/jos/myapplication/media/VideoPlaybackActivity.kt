package com.jos.myapplication.media

import android.app.Activity
import com.jos.myapplication.R
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView

/** Explicit, non-exported full-screen player. Only our scoped FileProvider URIs are accepted. */
class VideoPlaybackActivity:Activity() {
    private lateinit var video:VideoView
    private lateinit var status:TextView
    private lateinit var retry:Button
    private var uri:Uri?=null
    private var position=0
    private var prepared=false
    private var resumePlayback=true
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        uri=intent.data?.takeIf{it.scheme=="content"&&it.authority==packageName+".media"}
        if(uri==null){finish();return}
        position=savedInstanceState?.getInt("position")?:0
        val root=FrameLayout(this).apply{setBackgroundColor(Color.BLACK);fitsSystemWindows=true}
        video=VideoView(this)
        root.addView(video,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        val controls=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(0x99000000.toInt())}
        status=TextView(this).apply{setTextColor(Color.WHITE);text=getString(R.string.video_loading);setPadding(16,12,16,12)}
        controls.addView(status)
        val row=LinearLayout(this)
        row.addView(Button(this).apply{text=getString(R.string.video_back);setOnClickListener{finish()}})
        retry=Button(this).apply{text=getString(R.string.video_retry);visibility=View.GONE;setOnClickListener{load()}}
        row.addView(retry);controls.addView(row)
        root.addView(controls,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        setContentView(root)
        video.setMediaController(MediaController(this).apply{setAnchorView(video)})
        video.setOnPreparedListener{
            prepared=true;status.text=getString(R.string.video_controls);retry.visibility=View.GONE
            video.seekTo(position)
            if(resumePlayback)video.start()
        }
        video.setOnErrorListener{_,_,_->
            prepared=false;status.text=getString(R.string.video_error)
            retry.visibility=View.VISIBLE;true
        }
        video.setOnCompletionListener{resumePlayback=false;status.text=getString(R.string.video_finished)}
        load()
    }
    private fun load(){
        prepared=false;video.stopPlayback();status.text=getString(R.string.video_loading);retry.visibility=View.GONE
        try{video.setVideoURI(uri)}catch(_:Exception){status.text=getString(R.string.video_unavailable);retry.visibility=View.VISIBLE}
    }
    override fun onResume(){super.onResume();if(prepared&&resumePlayback){video.seekTo(position);video.start()}}
    override fun onPause(){if(prepared){position=video.currentPosition;resumePlayback=video.isPlaying;video.pause()};super.onPause()}
    override fun onSaveInstanceState(outState:Bundle){outState.putInt("position",if(prepared)video.currentPosition else position);super.onSaveInstanceState(outState)}
    override fun onDestroy(){video.stopPlayback();super.onDestroy()}
}
