package com.savie.precut.ui.editor.crop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.frame.CropRatio
import com.savie.precut.ui.components.IconButtonStyle
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.editor.tools.RatioGlyph
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glass

/** Crop mode's glass panel (RD-T12b): Ratio chips, then Reset, Mirror and Rotate as icon buttons. */
@Composable
fun CropPanel(ratio: CropRatio, actions: CropActions) {
    Column(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp).glass(Radius.panel).padding(top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.crop_ratio).uppercase(), style = PreCutType.section, color = TextMuted, modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CropRatio.entries.forEach { option ->
                val selected = option == ratio
                when (option) {
                    CropRatio.FREE -> PreCutChip(stringResource(R.string.crop_free), selected, { actions.onRatio(option) }, icon = R.drawable.ic_crop_free)
                    CropRatio.ORIGINAL -> PreCutChip(stringResource(R.string.crop_original), selected, { actions.onRatio(option) }, icon = R.drawable.ic_crop_original)
                    else -> PreCutChip(
                        "${option.widthRatio}:${option.heightRatio}",
                        selected,
                        { actions.onRatio(option) },
                        leading = { RatioGlyph(option.widthRatio, option.heightRatio, if (selected) OnAccent else TextPrimary, longSide = 16f) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PreCutIconButton(R.drawable.ic_restart_alt, stringResource(R.string.action_reset), actions::onReset)
            Spacer(Modifier.weight(1f))
            PreCutIconButton(R.drawable.ic_mirror, stringResource(R.string.edit_mirror), actions::onMirror, style = IconButtonStyle.Glass, small = true)
            PreCutIconButton(R.drawable.ic_rotate, stringResource(R.string.crop_rotate), actions::onRotate, style = IconButtonStyle.Glass, small = true)
        }
    }
}
