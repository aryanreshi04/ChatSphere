package com.jos.myapplication.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

class ConnectivityMonitor(context: Context, scope: CoroutineScope) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private fun connected(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } ?: false
    val online = callbackFlow {
        var active = manager.activeNetwork
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (active != network) trySend(false)
                active = network
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (active == network) trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }
            override fun onLost(network: Network) {
                // Re-querying activeNetwork here races with ConnectivityManager's state update.
                // Ignore a superseded network's loss during handover; clear the current one directly.
                if (active == network) { active = null; trySend(false) }
            }
            override fun onUnavailable() { active = null; trySend(false) }
        }
        trySend(connected())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, connected())
}
