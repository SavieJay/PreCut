package com.savie.precut.data

import android.content.Context
import android.os.RemoteException
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.savie.precut.model.growth.templateFromReferrer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Google Play's install referrer for this install: the string the store link carried, or null. */
fun interface InstallReferrerSource {
    suspend fun referrer(): String?
}

/**
 * Play's Install Referrer API (library 2.2). Null when Play didn't install this copy (the
 * website's APK), the service is missing or busy, or it doesn't answer within 10 seconds.
 */
class PlayInstallReferrerSource @Inject constructor(@ApplicationContext private val context: Context) : InstallReferrerSource {
    override suspend fun referrer(): String? = withTimeoutOrNull(TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val client = InstallReferrerClient.newBuilder(context).build()
            continuation.invokeOnCancellation { client.endConnection() }
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val referrer = if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        try {
                            client.installReferrer.installReferrer
                        } catch (e: RemoteException) {
                            Log.w(TAG, "Play's install referrer couldn't be read", e)
                            null
                        }
                    } else {
                        null
                    }
                    client.endConnection()
                    if (continuation.isActive) continuation.resume(referrer)
                }

                override fun onInstallReferrerServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }
    }

    private companion object {
        const val TAG = "InstallReferrer"
        const val TIMEOUT_MS = 10_000L
    }
}

/**
 * Phase 28: someone without PreCut tapped a shared template's link, went to Google Play and
 * installed. On the first start (a fresh install: no projects yet), Play's install referrer says
 * which template; it's opened like the link would have been. Read once, on the phone; nothing is
 * sent anywhere. Installs from the website's buttons are counted by Play Console's own reports
 * from the links' UTM tags, not by the app.
 */
@Singleton
class InstallReferrerCheck @Inject constructor(
    private val settings: AppSettings,
    private val source: InstallReferrerSource,
    private val projects: ProjectRepository,
    private val links: TemplateLinkInbox,
) {
    suspend fun runOnce() {
        if (settings.values.value.installReferrerChecked) return
        settings.update { it.copy(installReferrerChecked = true) }
        if (projects.hasAny()) return
        templateFromReferrer(source.referrer())?.let(links::offer)
    }
}
