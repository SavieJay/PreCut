package com.savie.precut.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.components.PreCutProgressBar
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glass
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

private val BannerShape = RoundedCornerShape(20.dp)

/**
 * Home's "a new version is ready" card (Phase 23, C3), a glass card with 20 dp corners: Update starts Play's background download,
 * which then shows its progress; once downloaded, Restart installs it. Close hides the offer until
 * a newer release (a download already going stays visible).
 */
@Composable
fun UpdateBanner(progress: UpdateProgress, onUpdate: () -> Unit, onRestart: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).glass(BannerShape)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val text = when (progress) {
                UpdateProgress.Idle -> stringResource(R.string.update_available)
                is UpdateProgress.Downloading -> stringResource(R.string.update_downloading, (progress.fraction * 100).roundToInt())
                UpdateProgress.Ready -> stringResource(R.string.update_downloaded)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            when (progress) {
                UpdateProgress.Idle -> AccentTextButton(stringResource(R.string.update_action), onUpdate)
                UpdateProgress.Ready -> AccentTextButton(stringResource(R.string.update_restart), onRestart)
                is UpdateProgress.Downloading -> Unit
            }
            if (progress == UpdateProgress.Idle) {
                PreCutIconButton(R.drawable.ic_close, stringResource(R.string.update_dismiss), onDismiss, small = true, tint = TextSecondary)
            }
        }
        if (progress is UpdateProgress.Downloading) {
            PreCutProgressBar(progress.fraction, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), height = 6.dp)
        }
    }
}
