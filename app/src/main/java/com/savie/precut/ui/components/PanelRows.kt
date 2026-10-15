package com.savie.precut.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/** A 40 dp slider row: optional label (or icon) at the start, the slider, the value at the end. */
@Composable
fun SliderRow(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    @DrawableRes icon: Int? = null,
    labelWidth: Dp = 72.dp,
    valueWidth: Dp = 44.dp,
    slider: @Composable (Modifier) -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        label?.let { Text(it, style = PreCutType.labelChip, color = TextSecondary, maxLines = 1, modifier = Modifier.width(labelWidth)) }
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = TextSecondary) }
        slider(Modifier.weight(1f))
        Text(value, style = PreCutType.labelChip.tabular(), color = TextPrimary, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(valueWidth))
    }
}

/** A 48 dp row with a label (and optional description) and a switch at the end. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (enabled) TextPrimary else TextDisabled)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
        }
        PreCutSwitch(checked, onCheckedChange, enabled = enabled)
    }
}

/**
 * A 64 dp preset tile (voice effects, transitions…): icon on Ink2, label below; selected =
 * AccentContainer + ring. [badge] is a small corner icon (e.g. "plays a preview").
 */
@Composable
fun PresetTile(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes badge: Int? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.width(64.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(shape)
                .background(if (selected) AccentContainer else Ink2)
                .then(if (selected) Modifier.border(2.dp, Accent, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = TextPrimary)
            badge?.let {
                Icon(painterResource(it), contentDescription = null, tint = TextSecondary, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(12.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
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

/** A 32 dp colour swatch; the selected one gets a white ring outside an Ink1 gap. */
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
            .size(36.dp)
            .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
            .clip(CircleShape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 28.dp else 32.dp)
                .clip(CircleShape)
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        )
        if (selected) Box(Modifier.size(32.dp).border(2.dp, Ink1, CircleShape))
    }
}
