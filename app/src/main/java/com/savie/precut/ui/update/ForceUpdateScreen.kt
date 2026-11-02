package com.savie.precut.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** Where the blocking screen's update stands: Play may already be downloading it in the background. */
sealed interface UpdateProgress {
    data object Idle : UpdateProgress

    /** Downloading; [fraction] from 0 to 1. */
    data class Downloading(val fraction: Float) : UpdateProgress

    /** Downloaded: restarting the app installs it. */
    data object Ready : UpdateProgress
}

/**
 * The blocking update screen (Phase 23, C1 and C2): over everything, not dismissable. Back and
 * every touch behind it do nothing; only Update now (or Restart to update, once Play has the
 * update downloaded) does something. [installed] and [latest] are version names; [latest] is
 * left out when the server doesn't say.
 */
@Composable
fun ForceUpdateScreen(
    message: String?,
    installed: String,
    latest: String?,
    progress: UpdateProgress,
    onUpdate: () -> Unit,
    onRestart: () -> Unit,
) {
    // Composed after the app's own handlers, so it wins: Back does nothing.
    BackHandler(enabled = true) {}
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink0)
            // Swallows touches meant for the screens underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Icon(
            painterResource(R.drawable.ic_logo),
            contentDescription = stringResource(R.string.app_name),
            tint = Accent,
            modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = 24.dp).size(36.dp),
        )
        Column(
            Modifier.align(Alignment.Center).systemBarsPadding().padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UpdateIllustration(Modifier.padding(bottom = 8.dp))
            Text(stringResource(R.string.update_required_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                message ?: stringResource(R.string.update_required_body),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                if (latest != null) stringResource(R.string.update_versions, installed, latest) else stringResource(R.string.update_version_installed, installed),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = TextSecondary,
            )
            when (progress) {
                is UpdateProgress.Downloading -> Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                        trackColor = Ink3,
                    )
                    Text(
                        stringResource(R.string.update_downloading, (progress.fraction * 100).roundToInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                UpdateProgress.Ready -> FilledButton(stringResource(R.string.update_restart_to_install), onRestart, Modifier.fillMaxWidth().padding(top = 8.dp), large = true)
                UpdateProgress.Idle -> FilledButton(stringResource(R.string.update_now), onUpdate, Modifier.fillMaxWidth().padding(top = 8.dp), large = true)
            }
        }
    }
}

/** The screen's state from Play's background download: [fraction] while downloading, [downloaded] once done. */
fun updateProgress(fraction: Float?, downloaded: Boolean): UpdateProgress = when {
    downloaded -> UpdateProgress.Ready
    fraction != null -> UpdateProgress.Downloading(fraction)
    else -> UpdateProgress.Idle
}
