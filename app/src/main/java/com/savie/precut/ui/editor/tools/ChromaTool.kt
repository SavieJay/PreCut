package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.frame.ChromaKey
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.components.formatHexColor
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** What the Chroma key panel reports. */
interface ChromaActions {
    fun onPicking(picking: Boolean)
    fun onIntensity(intensity: Float)
    fun onShadow(shadow: Float)
    fun onReset()
}

/**
 * Chroma key (P-04): the eyedropper (on, the preview picks the colour under the finger), the
 * picked colour, Intensity and Shadow. The sliders wait for a colour.
 */
@Composable
fun ChromaTool(chroma: ChromaKey?, picking: Boolean, actions: ChromaActions, onDone: () -> Unit) {
    ToolPanel(
        stringResource(R.string.tool_chroma),
        onDone,
        actions = { AccentTextButton(stringResource(R.string.action_reset), onClick = actions::onReset, enabled = chroma != null, color = TextSecondary) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconToggleButton(
                    checked = picking,
                    onCheckedChange = actions::onPicking,
                    modifier = Modifier.size(48.dp).background(if (picking) AccentContainer else Ink2, CircleShape),
                ) {
                    Icon(painterResource(R.drawable.ic_colorize), contentDescription = stringResource(R.string.chroma_pick), tint = if (picking) Accent else TextSecondary)
                }
                chroma?.let {
                    Box(Modifier.size(24.dp).background(Color(it.colorArgb), CircleShape).border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape))
                }
                Text(
                    chroma?.let { stringResource(R.string.chroma_picked, formatHexColor(it.colorArgb)) } ?: stringResource(R.string.chroma_hint),
                    style = PreCutType.labelChip,
                    color = TextSecondary,
                )
            }
            ChromaSlider(R.string.chroma_intensity, chroma?.intensity ?: 0.5f, enabled = chroma != null, onChanged = actions::onIntensity)
            ChromaSlider(R.string.chroma_shadow, chroma?.shadow ?: 0.2f, enabled = chroma != null, onChanged = actions::onShadow)
        }
    }
}

@Composable
private fun ChromaSlider(label: Int, value: Float, enabled: Boolean, onChanged: (Float) -> Unit) {
    var shown by remember(value) { mutableFloatStateOf(value) }
    SliderRow(value = if (enabled) "${(shown * 100).roundToInt()}" else "–", label = stringResource(label)) { sliderModifier ->
        PreCutSlider(
            value = shown,
            onValueChange = { shown = it },
            onValueChangeFinished = { onChanged(shown) },
            enabled = enabled,
            modifier = sliderModifier,
        )
    }
}
