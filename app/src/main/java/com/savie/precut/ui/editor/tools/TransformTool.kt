package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glassRow
import kotlin.math.roundToInt

/** Transform (RD-T15): Fit/Fill (each starts over), the transform as four read-only fields, and the gesture hint. */
@Composable
fun TransformTool(fit: FrameFit, transform: ClipTransform, onFit: (FrameFit) -> Unit, onReset: () -> Unit, onDone: () -> Unit) {
    ToolPanel(
        stringResource(R.string.tool_transform),
        onDone,
        actions = { ResetIconButton(onReset, enabled = fit != FrameFit.FIT || !transform.isIdentity) },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val plain = transform.isIdentity
            PreCutChip(stringResource(R.string.transform_fit), selected = plain && fit == FrameFit.FIT, onClick = { onFit(FrameFit.FIT) }, icon = R.drawable.ic_fit)
            PreCutChip(stringResource(R.string.transform_fill), selected = plain && fit == FrameFit.FILL, onClick = { onFit(FrameFit.FILL) }, icon = R.drawable.ic_crop_free)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadoutField(stringResource(R.string.rd_clip_transform_x), signed((transform.offsetX * 100).roundToInt()), Modifier.weight(1f))
            ReadoutField(stringResource(R.string.rd_clip_transform_y), signed((transform.offsetY * 100).roundToInt()), Modifier.weight(1f))
            ReadoutField(stringResource(R.string.rd_clip_transform_scale), "${(transform.scale * 100).roundToInt()}%", Modifier.weight(1f))
            ReadoutField(stringResource(R.string.rd_clip_transform_rotate), "${signed(transform.rotationDegrees.roundToInt())}°", Modifier.weight(1f))
        }
        Text(stringResource(R.string.transform_hint), style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

/** The design's `.nf`: a 56 dp quiet field with a small caps label over a Mono value. */
@Composable
private fun ReadoutField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(56.dp)
            .glassRow(FieldShape)
            .padding(horizontal = 14.dp)
            .clearAndSetSemantics { contentDescription = "$label $value" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(label.uppercase(), style = PreCutType.section.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.SemiBold), color = TextMuted, maxLines = 1)
        Text(value, style = PreCutType.timecode.copy(fontSize = 15.sp, lineHeight = 20.sp), color = TextPrimary, maxLines = 1)
    }
}

/** A typographic minus for negative values. */
private fun signed(value: Int): String = if (value < 0) "−${-value}" else value.toString()

private val FieldShape = RoundedCornerShape(18.dp)
