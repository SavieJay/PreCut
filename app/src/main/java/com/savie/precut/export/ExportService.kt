package com.savie.precut.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.savie.precut.MainActivity
import com.savie.precut.R
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.components.projectDisplayName
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps an export running while PreCut is in the background (EX-03): a foreground service whose
 * notification follows [ExportController] (progress with Cancel, then "saved" with Open and
 * Share, or "failed"). It stops when the export ends.
 */
@AndroidEntryPoint
class ExportService : Service() {

    @Inject lateinit var controller: ExportController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            controller.cancel()
            finish()
            return START_NOT_STICKY
        }
        ensureChannel(this)
        // Not ServiceCompat: it masks out types newer than it knows (mediaProcessing), leaving none.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PROGRESS_ID, progress(controller.state.value), foregroundType())
        } else {
            startForeground(PROGRESS_ID, progress(controller.state.value))
        }
        if (watching == null) watching = scope.launch { controller.state.collect(::show) }
        return START_NOT_STICKY
    }

    /** Android 15+ ends media processing after its time limit; cancel cleanly. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        controller.cancel()
        finish()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun show(state: ExportJobState) {
        val manager = NotificationManagerCompat.from(this)
        when (state) {
            is ExportJobState.Running, is ExportJobState.Saving -> notify(manager, PROGRESS_ID, progress(state))
            is ExportJobState.Done -> {
                finish()
                notify(manager, RESULT_ID, done(state))
            }
            is ExportJobState.Failed -> {
                finish()
                notify(manager, RESULT_ID, failed(state))
            }
            ExportJobState.Idle -> finish()
        }
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notify(manager: NotificationManagerCompat, id: Int, notification: Notification) {
        // Without the permission the export still runs; there's just no notification.
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(id, notification)
            } catch (_: SecurityException) {
                // Permission withdrawn meanwhile.
            }
        }
    }

    private fun progress(state: ExportJobState): Notification {
        val name = when (state) {
            is ExportJobState.Running -> state.job.displayName()
            is ExportJobState.Saving -> state.job.displayName()
            else -> ""
        }
        val percent = (state as? ExportJobState.Running)?.percent
        val cancel = PendingIntent.getService(
            this, 0, Intent(this, ExportService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE,
        )
        return base()
            .setContentTitle(getString(R.string.export_notif_running, name))
            .setContentText(if (state is ExportJobState.Saving) getString(R.string.export_notif_saving) else percent?.let { "$it%" })
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.action_cancel), cancel)
            .build()
    }

    private fun done(state: ExportJobState.Done): Notification {
        val audio = state.job.kind == ExportKind.AUDIO
        val mime = if (audio) "audio/mp4" else "video/mp4"
        val text = getString(
            R.string.export_notif_done_text,
            state.job.displayName(), formatDurationMs(state.job.durationMs), Formatter.formatShortFileSize(this, state.sizeBytes),
            getString(if (audio) R.string.export_album_music else R.string.export_album_movies),
        )
        val builder = base()
            .setContentTitle(getString(if (audio) R.string.export_notif_done_audio else R.string.export_notif_done_video))
            .setContentText(text)
            .setAutoCancel(true)
        state.uri?.toUri()?.let { uri ->
            val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val share = Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                getString(R.string.export_share_title),
            )
            val openIntent = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(openIntent)
                .addAction(0, getString(R.string.action_open), openIntent)
                .addAction(0, getString(R.string.action_share), PendingIntent.getActivity(this, 2, share, PendingIntent.FLAG_IMMUTABLE))
        }
        return builder.build()
    }

    private fun failed(state: ExportJobState.Failed): Notification = base()
        .setContentTitle(getString(R.string.export_failed_title))
        .setContentText(state.job?.displayName())
        .setAutoCancel(true)
        .build()

    private fun base(): NotificationCompat.Builder {
        val app = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_movie)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(app)
    }

    private fun ExportJob.displayName() = projectDisplayName(this@ExportService, name, createdAt)

    private fun foregroundType(): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else -> 0
    }

    companion object {
        private const val CHANNEL = "exports"
        private const val PROGRESS_ID = 41
        private const val RESULT_ID = 42
        private const val ACTION_CANCEL = "com.savie.precut.export.CANCEL"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.export_notif_channel), NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }
}

/** Starts [ExportService] (the app is in the foreground when an export starts). */
class AndroidExportServiceStarter @Inject constructor(@ApplicationContext private val context: Context) : ExportServiceStarter {
    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, ExportService::class.java))
    }
}
