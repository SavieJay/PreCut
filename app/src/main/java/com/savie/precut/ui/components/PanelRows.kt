package com.savie.precut.ui.components

import com.savie.precut.ui.theme.glassRow
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** A 44 dp slider row (RD-TOK `.sl`): label (or icon) at the start, the slider, the value at the end. */
@Composable
fun SliderRow(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    @DrawableRes icon: Int? = null,
    labelWidth: Dp = 76.dp,
    valueWidth: Dp = 48.dp,
    slider: @Composable (Modifier) -> Unit,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        label?.let { Text(it, style = PreCutType.labelChip.copy(fontWeight = FontWeight.SemiBold), color = TextSecondary, maxLines = 1, modifier = Modifier.width(labelWidth)) }
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = TextSecondary) }
        slider(Modifier.weight(1f))
        Text(value, style = PreCutType.timecode, color = TextPrimary, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(valueWidth))
    }
}

/** A 52 dp row (RD-TOK `.swr`) with a label, an optional description and a switch at the end. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    ai: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) TextPrimary else TextDisabled)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        }
        PreCutSwitch(checked, onCheckedChange, enabled = enabled, ai = ai)
    }
}

/** [items] in rows of [columns] equal cells, each item centred (the design's tile grids). */
@Composable
fun <T> TileGrid(items: List<T>, columns: Int, modifier: Modifier = Modifier, cell: @Composable (T) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item -> Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { cell(item) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A 36 dp colour swatch (RD-TOK `.swt`); the selected one gets a pink ring outside a dark gap. */
@Composable
fun ColorDot(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush? = null,
) {
    Box(
        modifier
            .size(40.dp)
            .then(if (selected) Modifier.border(2.dp, Accent, CircleShape) else Modifier)
            .clip(CircleShape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 30.dp else 36.dp)
                .clip(CircleShape)
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        )
    }
}
