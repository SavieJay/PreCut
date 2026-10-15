package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.model.SpeedScale
import com.savie.precut.model.speed.SpeedCurve
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.PillTab
import com.savie.precut.ui.components.PillTabs
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.PreCutSwitch
import com.savie.precut.ui.components.SliderTick
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.components.formatSpeed
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular
import kotlin.math.roundToLong

/** Presets for clips (CapCut's 0.1x–100x) and for audio clips. */
val CLIP_SPEED_PRESETS = listOf(0.1f, 0.5f, 1f, 2f, 5f, 10f, 100f)
val AUDIO_SPEED_PRESETS = listOf(0.5f, 0.75f, 1f, 1.5f, 2f)

/**
 * T-02 / T-03 / U-07: Normal | Curve tabs (curve: [SpeedCurveTab]), preset chips, and a logarithmic
 * slider filled from 1x that snaps to the presets. Shows the new length when [sourceLengthMs] is
 * given and a "Keep pitch" switch when [keepPitch] is non-null. Applies on chip tap or release.
 */
@Composable
fun SpeedTool(
    speed: Float,
    onSpeedChanged: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    scale: SpeedScale = SpeedScale.CLIP,
    presets: List<Float> = CLIP_SPEED_PRESETS,
    sourceLengthMs: Long? = null,
    keepPitch: Boolean? = null,
    onKeepPitchChanged: (Boolean) -> Unit = {},
    /** Shows the Normal | Curve tabs (single video clips). */
    showCurveTab: Boolean = false,
    curve: SpeedCurve? = null,
    onCurve: (SpeedCurve) -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(if (curve != null) 1 else 0) }
    var position by remember(speed) { mutableFloatStateOf(scale.toSlider(speed)) }
    val shown = scale.fromSlider(position)
    ToolPanel(
        stringResource(R.string.speed_title),
        onDone,
        modifier,
        actions = {
            AccentTextButton(stringResource(R.string.adjust_reset), { onSpeedChanged(1f) }, enabled = speed != 1f || curve != null, color = TextSecondary)
        },
    ) {
        if (showCurveTab) {
            PillTabs(
                listOf(PillTab(stringResource(R.string.speed_normal)), PillTab(stringResource(R.string.speed_curve))),
                selected = tab,
                onSelect = { tab = it },
            )
            if (tab == 1) {
                SpeedCurveTab(curve, sourceLengthMs ?: 0L, onCurve)
                KeepPitchRow(keepPitch, onKeepPitchChanged)
                return@ToolPanel
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                PreCutChip(stringResource(R.string.speed_value, formatSpeed(preset)), selected = preset == speed, onClick = { onSpeedChanged(preset) })
            }
        }
        val label = { s: Float -> "${formatSpeed(s)}x" }
        PreCutSlider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onSpeedChanged(scale.fromSlider(position)) },
            anchor = scale.toSlider(1f),
            bubble = { label(scale.fromSlider(it)) },
            onReset = { onSpeedChanged(1f) },
            ticks = presets.map { SliderTick(scale.toSlider(it), label(it)) },
            snapTo = presets.map(scale::toSlider),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            sourceLengthMs?.let { length ->
                Text(
                    buildAnnotatedString {
                        append(formatSeconds(length))
                        withStyle(SpanStyle(color = TextSecondary, fontWeight = FontWeight.Normal)) { append(" → ") }
                        append(formatSeconds((length / shown.toDouble()).roundToLong()))
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp).tabular(),
                )
            }
            Spacer(Modifier.weight(1f))
            keepPitch?.let { checked -> KeepPitchSwitch(checked, onKeepPitchChanged) }
        }
    }
}

@Composable
private fun KeepPitchRow(keepPitch: Boolean?, onKeepPitchChanged: (Boolean) -> Unit) {
    val checked = keepPitch ?: return
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        KeepPitchSwitch(checked, onKeepPitchChanged)
    }
}

@Composable
private fun KeepPitchSwitch(checked: Boolean, onKeepPitchChanged: (Boolean) -> Unit) {
    Text(stringResource(R.string.speed_keep_pitch), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.width(12.dp))
    PreCutSwitch(checked = checked, onCheckedChange = onKeepPitchChanged)
}
