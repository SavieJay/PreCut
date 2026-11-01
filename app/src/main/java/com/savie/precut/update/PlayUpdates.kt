package com.savie.precut.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.savie.precut.data.WebConfig
import com.savie.precut.data.await
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play's in-app updates: the immediate flow for a required update, the flexible one for the
 * Home banner, and where to go when Play can't do it (a copy not installed from Play, or Play not
 * offering the update yet).
 */
@Singleton
class PlayUpdates internal constructor(
    private val context: Context,
    /** Play's manager; tests pass `FakeAppUpdateManager`. */
    managerFactory: () -> AppUpdateManager,
    /** Who installed this copy; tests pass Play's package. */
    private val installer: () -> String?,
    /** The website, whose download page updates a copy not installed from Play. */
    private val webHost: String,
) {
    @Inject
    constructor(@ApplicationContext context: Context, web: WebConfig) :
        this(context, { AppUpdateManagerFactory.create(context) }, { installerOf(context) }, web.host)

    private val manager: AppUpdateManager by lazy { managerFactory().also { it.registerListener(listener) } }

    private val _downloaded = MutableStateFlow(false)

    /** A flexible update finished downloading: restarting installs it. */
    val downloaded: StateFlow<Boolean> = _downloaded.asStateFlow()

    // App-wide, like the manager: it lives as long as the process.
    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) _downloaded.value = true
    }

    /** Whether Google Play installed this copy (only then can Play update it in the app). */
    val installedFromPlay: Boolean get() = installer() == PLAY_STORE

    /**
     * Starts Play's [type] update flow (`AppUpdateType.IMMEDIATE` or `FLEXIBLE`) through [launcher];
     * a download that already finished is installed. False when Play can't, so the caller opens
     * [storeIntent] instead.
     */
    suspend fun start(type: Int, launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        if (!installedFromPlay) return false
        val info = try {
            manager.appUpdateInfo.await()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Play couldn't say if there's an update", e)
            return false
        }
        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            manager.completeUpdate()
            return true
        }
        val availability = info.updateAvailability()
        val possible = availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ||
            (availability == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(type))
        return possible && manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(type))
    }

    /** Installs a downloaded flexible update; the app restarts. */
    fun completeUpdate() {
        manager.completeUpdate()
    }

    /**
     * The Play listing; for a copy not installed from Play (the website's APK), [downloadUrl] when the
     * server gives one, else the website's download page.
     */
    fun storeIntent(downloadUrl: String?): Intent {
        val target = if (installedFromPlay) "market://details?id=${context.packageName}" else downloadUrl ?: "https://$webHost/download"
        return Intent(Intent.ACTION_VIEW, target.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The Play listing on the web, for phones with no app to open `market://`. */
    fun webListingIntent(): Intent =
        Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    internal companion object {
        private const val TAG = "PlayUpdates"
        const val PLAY_STORE = "com.android.vending"

        /** The package that installed this copy (Play is `com.android.vending`), or null. */
        fun installerOf(context: Context): String? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                // The only way below API 30 (minSdk is 26); its replacement needs API 30.
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
