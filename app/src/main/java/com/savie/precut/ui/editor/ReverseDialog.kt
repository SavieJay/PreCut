package com.savie.precut.ui.editor

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savie.precut.R
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.components.PreCutProgressRing
import com.savie.precut.ui.theme.GlassLevel
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.glass
import androidx.compose.ui.text.style.TextAlign
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/** RD-T19: reversing progress; only Cancel closes it. */
@Composable
fun ReverseDialog(progress: ReverseProgress, onCancel: () -> Unit) = WorkProgressDialog(
    R.drawable.ic_reverse,
    stringResource(R.string.reverse_progress, progress.percent),
    stringResource(R.string.reverse_body, formatSeconds(progress.clipLengthMs)),
    progress.percent,
    progress.secondsLeft,
    onCancel,
)

/** Remove background (Phase 22A): progress of a clip's matte; only Cancel closes it. */
@Composable
fun CutoutDialog(progress: ReverseProgress, onCancel: () -> Unit) = WorkProgressDialog(
    R.drawable.ic_frame_person,
    stringResource(R.string.cutout_progress, progress.percent),
    stringResource(R.string.cutout_body, formatSeconds(progress.clipLengthMs)),
    progress.percent,
    progress.secondsLeft,
    onCancel,
)

/** Auto reframe (Phase 22B): following the person in one clip, or in [ReframeProgress.clips] with Apply to all. */
@Composable
fun ReframeDialog(progress: ReframeProgress, onCancel: () -> Unit) = WorkProgressDialog(
    R.drawable.ic_reframe,
    stringResource(if (progress.following) R.string.follow_progress else R.string.reframe_progress, progress.percent),
    if (progress.following) {
        stringResource(R.string.follow_body)
    } else if (progress.clips == 1) {
        stringResource(R.string.reframe_body, formatSeconds(progress.clipLengthMs))
    } else {
        LocalResources.current.getQuantityString(R.plurals.reframe_body_all, progress.clips, progress.clips)
    },
    progress.percent,
    progress.secondsLeft,
    onCancel,
)

/** Reduce noise (Phase 22B): measuring a clip's background noise. */
@Composable
fun NoiseDialog(progress: ReverseProgress, onCancel: () -> Unit) = WorkProgressDialog(
    R.drawable.ic_noise,
    stringResource(R.string.noise_progress, progress.percent),
    stringResource(R.string.noise_body, formatSeconds(progress.clipLengthMs)),
    progress.percent,
    progress.secondsLeft,
    onCancel,
)

/**
 * Long work on a clip done on the phone (RD-T19): a solid raised dialog (it sits over the video)
 * with a 120 dp progress ring around the work's icon, [title] with the percent, [body] and time
 * left; only the ✕ (Cancel) closes it.
 */
@Composable
private fun WorkProgressDialog(@DrawableRes icon: Int, title: String, body: String, percent: Int, secondsLeft: Int?, onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxWidth().glass(Radius.sheet, GlassLevel.Raised, solid = true)) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    PreCutProgressRing(percent / 100f, size = 120.dp, stroke = 8.dp)
                    Box(Modifier.size(64.dp).background(AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), contentDescription = null, tint = Accent, modifier = Modifier.size(32.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge.tabular(), textAlign = TextAlign.Center)
                    val left = secondsLeft?.let { LocalResources.current.getQuantityString(R.plurals.reverse_seconds_left, it.coerceAtLeast(1), it.coerceAtLeast(1)) }
                    Text(
                        listOfNotNull(body, left).joinToString(" "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            PreCutIconButton(
                R.drawable.ic_close,
                stringResource(R.string.action_cancel),
                onCancel,
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
            )
        }
    }
}
