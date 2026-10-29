package com.jos.myapplication.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

object DeviceLocation {
    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun permitted(context: Context) = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Only collected while a visible screen requests it. No background service. */
    @SuppressLint("MissingPermission")
    fun updates(context: Context) = callbackFlow<Location> {
        check(permitted(context)) { "Location permission is required" }
        val manager = context.getSystemService(LocationManager::class.java)
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = buildList {
            if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        check(providers.isNotEmpty()) { "Enable device location services and try again" }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { trySend(location) }
            override fun onProviderDisabled(provider: String) {
                if (providers.none { manager.isProviderEnabled(it) }) close(IllegalStateException("Device location is switched off"))
            }
        }
        try {
            providers.forEach { manager.requestLocationUpdates(it, 5_000L, 5f, listener, Looper.getMainLooper()) }
        } catch (e: Exception) {
            manager.removeUpdates(listener)
            throw e
        }
        awaitClose { manager.removeUpdates(listener) }
    }
    // Require a fresh fix instead of silently sharing an old last-known location.
    suspend fun current(context: Context): Location = withTimeout(25_000) { updates(context).first() }
}
