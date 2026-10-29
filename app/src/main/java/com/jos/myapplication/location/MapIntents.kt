package com.jos.myapplication.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.jos.myapplication.data.MessagePolicy

object MapIntents {
    fun open(context: Context, latitude: Double, longitude: Double, directions: Boolean = false, origin:Pair<Double,Double>?=null, mode:String="walking") {
        if (!MessagePolicy.validLocation(latitude, longitude)) return
        val uri = if (directions) Uri.parse(DirectionsPolicy.url(latitude,longitude,origin,mode))
        else Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: Exception) { Toast.makeText(context, "No map app or browser is available", Toast.LENGTH_LONG).show() }
    }
}
