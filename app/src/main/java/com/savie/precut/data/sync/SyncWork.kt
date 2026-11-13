package com.savie.precut.data.sync

import android.content.Context
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.savie.precut.ai.cloud.cloudJson
import com.savie.precut.model.account.AuthFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerializationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Asks for a cloud sync (Phase 30C). */
interface SyncRequests {
    /** Now: at sign-in, at app start. */
    fun now()

    /** A little after an edit, so a burst of autosaves becomes one sync. */
    fun afterEdit()

    /** Once the phone is on Wi-Fi (files wait for it, Settings → Account). */
    fun onWifi()
}

/**
 * One sync at a time, as unique work that waits for a network and survives the app closing. A
 * request while one is waiting keeps that one (it picks up the newest edits when it runs; edits
 * during a run are sent by the same run).
 */
@Singleton
class WorkManagerSyncRequests @Inject constructor(@ApplicationContext private val context: Context) : SyncRequests {
    override fun now() = enqueue(delaySec = 0, ExistingWorkPolicy.REPLACE)

    override fun afterEdit() = enqueue(delaySec = AFTER_EDIT_SEC, ExistingWorkPolicy.KEEP)

    override fun onWifi() = enqueue(delaySec = 0, ExistingWorkPolicy.KEEP, NetworkType.UNMETERED, WIFI_WORK_NAME)

    private fun enqueue(
        delaySec: Long,
        policy: ExistingWorkPolicy,
        network: NetworkType = NetworkType.CONNECTED,
        name: String = WORK_NAME,
    ) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setInitialDelay(delaySec, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SEC, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
    }

    private companion object {
        const val WORK_NAME = "cloud-project-sync"
        const val WIFI_WORK_NAME = "cloud-project-sync-wifi"
        const val AFTER_EDIT_SEC = 20L
        const val BACKOFF_SEC = 30L
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: ProjectSync,
    private val requests: SyncRequests,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val outcome = sync.run()) {
        SyncOutcome.Done, SyncOutcome.NotSignedIn -> Result.success()
        SyncOutcome.WaitingForWifi -> Result.success().also { requests.onWifi() }
        // Signed out by the server: nothing to retry until someone signs in (which asks again).
        is SyncOutcome.Retry -> if (outcome.failure == AuthFailure.SessionEnded) Result.success() else Result.retry()
    }
}

/** Cursors in private preferences `sync`, one per account. */
@Singleton
class PrefsSyncCursors @Inject constructor(@ApplicationContext context: Context) : SyncCursors {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    override fun get(userId: String): Cursor? = prefs.getString(key(userId), null)?.let {
        try {
            cloudJson.decodeFromString<Cursor>(it)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun set(userId: String, cursor: Cursor?) = prefs.edit {
        if (cursor == null) remove(key(userId)) else putString(key(userId), cloudJson.encodeToString(Cursor.serializer(), cursor))
    }

    private fun key(userId: String) = "cursor_$userId"
}
