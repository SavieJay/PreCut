package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular
import kotlin.math.roundToInt

/** Transform (T-15): Fit/Fill (each starts over), a readout of the transform, and the gesture hint. */
@Composable
fun TransformTool(fit: FrameFit, transform: ClipTransform, onFit: (FrameFit) -> Unit, onReset: () -> Unit, onDone: () -> Unit) {
    ToolPanel(
        stringResource(R.string.tool_transform),
        onDone,
        actions = {
            AccentTextButton(
                stringResource(R.string.action_reset),
                onClick = onReset,
                enabled = fit != FrameFit.FIT || !transform.isIdentity,
                color = TextSecondary,
            )
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val plain = transform.isIdentity
            PreCutChip(stringResource(R.string.transform_fit), selected = plain && fit == FrameFit.FIT, onClick = { onFit(FrameFit.FIT) }, icon = R.drawable.ic_fit)
            PreCutChip(stringResource(R.string.transform_fill), selected = plain && fit == FrameFit.FILL, onClick = { onFit(FrameFit.FILL) }, icon = R.drawable.ic_crop_free)
            Text(
                stringResource(
                    R.string.transform_readout,
                    (transform.scale * 100).roundToInt(),
                    transform.rotationDegrees.roundToInt(),
                    (transform.offsetX * 100).roundToInt(),
                    (transform.offsetY * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = TextSecondary,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Text(stringResource(R.string.transform_hint), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
