package com.savie.precut.ui.editor.captions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.editor.CaptionsUi
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/**
 * Captions at work (Phase 21A): preparing the sound (Cancel stops at once), then sending it part
 * by part (Stop ends after the part being sent). Only these buttons close it.
 */
@Composable
fun CaptionsProgressDialog(state: CaptionsUi, onStop: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            Modifier.fillMaxWidth().background(Ink2, RoundedCornerShape(28.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(48.dp).background(AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_closed_caption), contentDescription = null, tint = Accent)
            }
            when (state) {
                is CaptionsUi.Preparing -> {
                    Text(stringResource(R.string.captions_preparing, state.percent), style = MaterialTheme.typography.titleLarge.tabular())
                    Progress(state.percent / 100f)
                    Buttons(stringResource(R.string.action_cancel), enabled = true, onStop)
                }
                is CaptionsUi.Sending -> {
                    Text(stringResource(R.string.captions_sending), style = MaterialTheme.typography.titleLarge)
                    val part = if (state.parts > 1) stringResource(R.string.captions_sending_part, state.part + 1, state.parts) else null
                    Text(
                        listOfNotNull(part, stringResource(R.string.captions_sending_body)).joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Progress(null)
                    // Stop takes effect between parts, so it's offered only while more are to come.
                    if (state.part < state.parts - 1) {
                        val label = stringResource(if (state.stopping) R.string.captions_stopping else R.string.captions_stop)
                        Buttons(label, enabled = !state.stopping, onStop)
                    }
                }
                else -> Unit
            }
        }
    }
}

/** Determinate with [fraction], otherwise running. */
@Composable
private fun Progress(fraction: Float?) {
    val modifier = Modifier.fillMaxWidth().height(6.dp)
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = modifier,
            color = Accent,
            trackColor = Ink3,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    } else {
        LinearProgressIndicator(modifier = modifier, color = Accent, trackColor = Ink3, strokeCap = StrokeCap.Round, gapSize = 0.dp)
    }
}

@Composable
private fun Buttons(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        AccentTextButton(label, onClick = onClick, enabled = enabled)
    }
}
