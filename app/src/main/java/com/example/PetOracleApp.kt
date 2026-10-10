package com.example

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.manager.ModelDownloadManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PetOracleApp : Application() {

    /** Lives as long as the process, so a download keeps going if the screen is closed. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * One download manager for the whole process. Two managers writing the same partial file
     * would corrupt it, so the screen's view model must never create its own.
     */
    val downloads: ModelDownloadManager by lazy {
        ModelDownloadManager(
            modelsDir = File(filesDir, "models"),
            scope = appScope,
            isOnline = { hasInternet() },
            freeBytes = { filesDir.usableSpace }
        )
    }

    private fun hasInternet(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
