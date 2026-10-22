package com.savie.precut.ai.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Where this build's backend is (from the gitignored cloud.properties, via BuildConfig). */
data class CloudConfig(val url: String, val region: String, val playCloudProjectNumber: Long) {
    /** False for a build made without cloud.properties: cloud features say they aren't set up. */
    val isSetUp: Boolean get() = url.isNotBlank() && playCloudProjectNumber > 0
}

/** This install's identity on the backend: a random ID made on first use, and the token registration gave it. */
interface InstallStore {
    fun installId(): String
    var token: String?

    /** Forgets both: the next registration is a new install (after "Delete my cloud data"). */
    fun reset()
}

/** In private preferences `cloud`, which the backup rules leave out so a restored phone registers afresh. */
@Singleton
class PrefsInstallStore @Inject constructor(@ApplicationContext context: Context) : InstallStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    override fun installId(): String =
        prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also { id -> prefs.edit { putString(KEY_ID, id) } }

    override var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value) }

    override fun reset() = prefs.edit { clear() }

    private companion object {
        const val PREFS = "cloud"
        const val KEY_ID = "install_id"
        const val KEY_TOKEN = "install_token"
    }
}

/** Whether the phone has a network connection that should reach the internet. */
fun interface NetworkState {
    fun isOnline(): Boolean
}

@Singleton
class AndroidNetworkState @Inject constructor(@ApplicationContext private val context: Context) : NetworkState {
    override fun isOnline(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
