package com.savie.precut.ui.editor.crop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.frame.CropRatio
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.editor.tools.RatioGlyph
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.OnAccentContainer
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** Crop mode's panel (T-12b): Ratio chips with Reset, then Mirror and Rotate. */
@Composable
fun CropPanel(ratio: CropRatio, actions: CropActions) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.crop_ratio), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            AccentTextButton(stringResource(R.string.action_reset), onClick = actions::onReset, color = TextSecondary)
        }
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
                        leading = { RatioGlyph(option.widthRatio, option.heightRatio, if (selected) OnAccentContainer else TextPrimary) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)) {
            RoundButton(R.drawable.ic_mirror, stringResource(R.string.edit_mirror), actions::onMirror)
            RoundButton(R.drawable.ic_rotate, stringResource(R.string.crop_rotate), actions::onRotate)
        }
    }
}

@Composable
private fun RoundButton(icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, shape = CircleShape, colors = IconButtonDefaults.iconButtonColors(containerColor = Ink2)) {
        Icon(painterResource(icon), description)
    }
}
