package com.savie.precut.ui.editor

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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/** T-19: reversing progress; only Cancel closes it. */
@Composable
fun ReverseDialog(progress: ReverseProgress, onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            Modifier.fillMaxWidth().background(Ink2, RoundedCornerShape(28.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(48.dp).background(AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_reverse), contentDescription = null, tint = Accent)
            }
            Text(stringResource(R.string.reverse_progress, progress.percent), style = MaterialTheme.typography.titleLarge.tabular())
            val left = progress.secondsLeft?.let { LocalResources.current.getQuantityString(R.plurals.reverse_seconds_left, it.coerceAtLeast(1), it.coerceAtLeast(1)) }
            Text(
                listOfNotNull(stringResource(R.string.reverse_body, formatSeconds(progress.clipLengthMs)), left).joinToString(" "),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Accent,
                trackColor = Ink3,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AccentTextButton(stringResource(R.string.action_cancel), onClick = onCancel)
            }
        }
    }
}
