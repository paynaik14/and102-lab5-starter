package com.codepath.articlesearch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.widget.Toast

class NetworkReceiver(private val callback: NetworkCallback) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo

        if (activeNetwork != null && activeNetwork.isConnected) {
            callback.onNetworkAvailable()
        } else {
            callback.onNetworkUnavailable()
        }
    }

    interface NetworkCallback {
        fun onNetworkAvailable()
        fun onNetworkUnavailable()
    }
}