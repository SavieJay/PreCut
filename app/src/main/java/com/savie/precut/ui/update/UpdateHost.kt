package com.savie.precut.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.install.model.AppUpdateType
import com.savie.precut.BuildConfig
import com.savie.precut.model.update.UpdatePrompt
import com.savie.precut.update.PlayUpdates
import com.savie.precut.update.UpdateChecker
import kotlinx.coroutines.launch

/** The app's update checker and Play's updates, for screens that show update UI (Home's banner). */
class Updates(val checker: UpdateChecker, val play: PlayUpdates)

/** Provided by MainActivity; null in previews and tests. */
val LocalUpdates = staticCompositionLocalOf<Updates?> { null }

/**
 * Over the whole app (Phase 23): the blocking update screen when this version may no longer be
 * used, otherwise "What's new" once after an update.
 */
@Composable
fun UpdateHost(checker: UpdateChecker, play: PlayUpdates) {
    val ui by checker.state.collectAsStateWithLifecycle()
    val fraction by play.progress.collectAsStateWithLifecycle()
    val downloaded by play.downloaded.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A cancelled immediate update just leaves the blocking screen up.
    val launcher = rememberUpdateLauncher()
    when (val prompt = ui.prompt) {
        is UpdatePrompt.Required -> ForceUpdateScreen(
            message = prompt.message,
            installed = BuildConfig.VERSION_NAME,
            latest = prompt.latestVersionName,
            progress = updateProgress(fraction, downloaded),
            onUpdate = {
                scope.launch {
                    if (!play.start(AppUpdateType.IMMEDIATE, launcher)) openStore(context, play, prompt.downloadUrl)
                }
            },
            onRestart = play::completeUpdate,
        )
        else -> if (ui.whatsNew.isNotEmpty()) WhatsNewSheet(BuildConfig.VERSION_NAME, ui.whatsNew, checker::onWhatsNewSeen)
    }
}

/**
 * Home's banner while a newer version is out and not closed for it, or while Play downloads or
 * holds one: Play's flexible update, or the store.
 */
@Composable
fun HomeUpdateBanner(checker: UpdateChecker, play: PlayUpdates) {
    val ui by checker.state.collectAsStateWithLifecycle()
    val fraction by play.progress.collectAsStateWithLifecycle()
    val downloaded by play.downloaded.collectAsStateWithLifecycle()
    val progress = updateProgress(fraction, downloaded)
    if (!ui.banner && progress == UpdateProgress.Idle) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberUpdateLauncher()
    UpdateBanner(
        progress = progress,
        onUpdate = {
            scope.launch {
                if (!play.start(AppUpdateType.FLEXIBLE, launcher)) openStore(context, play, downloadUrl = null)
            }
        },
        onRestart = play::completeUpdate,
        onDismiss = checker::onBannerDismissed,
    )
}

@Composable
private fun rememberUpdateLauncher(): ActivityResultLauncher<IntentSenderRequest> =
    rememberLauncherForActivityResult(StartIntentSenderForResult()) {}

/** The Play listing (or the server's download page for a copy not from Play); the web listing without a store app. */
private fun openStore(context: Context, play: PlayUpdates, downloadUrl: String?) {
    try {
        context.startActivity(play.storeIntent(downloadUrl))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(play.webListingIntent())
        } catch (_: ActivityNotFoundException) {
            // No browser either: nothing more to try.
        }
    }
}
