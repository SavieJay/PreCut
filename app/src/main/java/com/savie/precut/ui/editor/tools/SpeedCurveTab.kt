package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.speed.SpeedCurve
import com.savie.precut.model.speed.SpeedPreset
import com.savie.precut.model.speed.SpeedSteps
import com.savie.precut.ui.components.PresetTile
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.theme.TextSecondary

/**
 * The Speed panel's Curve tab (T-03): preset tiles, the curve graph once a curve is set, and the
 * length change for a source of [sourceLengthMs].
 */
@Composable
fun SpeedCurveTab(curve: SpeedCurve?, sourceLengthMs: Long, onCurve: (SpeedCurve) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SpeedPreset.entries.forEach { preset ->
            val (icon, label) = preset.tile
            PresetTile(stringResource(label), icon, selected = curve?.preset == preset, onClick = {
                // Custom keeps an edited curve; the others start from their shape.
                onCurve(if (preset == SpeedPreset.CUSTOM && curve != null) curve.copy(preset = SpeedPreset.CUSTOM) else SpeedCurve.preset(preset))
            })
        }
    }
    if (curve == null) return
    SpeedCurveGraph(curve, onChange = onCurve)
    val curvedMs = SpeedSteps.of(curve, sourceLengthMs * 1_000).timelineLengthUs() / 1_000
    Text(
        stringResource(R.string.speed_length, formatSeconds(sourceLengthMs), formatSeconds(curvedMs)) + "   " + stringResource(R.string.speed_curve_hint),
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        maxLines = 1,
    )
}

private val SpeedPreset.tile: Pair<Int, Int>
    get() = when (this) {
        SpeedPreset.CUSTOM -> R.drawable.ic_edit to R.string.speed_preset_custom
        SpeedPreset.MONTAGE -> R.drawable.ic_curve to R.string.speed_preset_montage
        SpeedPreset.HERO -> R.drawable.ic_speed_hero to R.string.speed_preset_hero
        SpeedPreset.BULLET -> R.drawable.ic_speed_bullet to R.string.speed_preset_bullet
        SpeedPreset.JUMP_CUT -> R.drawable.ic_speed_jump to R.string.speed_preset_jump_cut
        SpeedPreset.FLASH_IN -> R.drawable.ic_tr_flash to R.string.speed_preset_flash_in
        SpeedPreset.FLASH_OUT -> R.drawable.ic_flash_off to R.string.speed_preset_flash_out
    }
