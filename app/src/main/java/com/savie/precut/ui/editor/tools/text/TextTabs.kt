package com.savie.precut.ui.editor.tools.text

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.components.pressScale
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glassRow

/** RD-X01's text tabs (`.tabs`): 44 dp labels, the chosen one white over a 3 dp pink underline. */
@Composable
internal fun TextUnderlineTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            labels.forEachIndexed { index, label ->
                val on = index == selected
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        .height(44.dp)
                        .selectable(on, role = Role.Tab) { onSelect(index) },
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(label, style = PreCutType.labelChip.copy(fontWeight = FontWeight.SemiBold), color = if (on) TextPrimary else TextMuted, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (on) Accent else Color.Transparent, UnderlineShape))
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.08f))
    }
}

private val UnderlineShape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)

/** One icon segment of RD-X04b's segmented rows: pink with a dark icon when on. */
internal data class IconSegment(@DrawableRes val icon: Int, val description: String, val on: Boolean, val onToggle: (Boolean) -> Unit)

/** A 48 dp segmented row of icon toggles (RD-X04b `.seg`); each segment toggles on its own. */
@Composable
internal fun IconSegments(segments: List<IconSegment>) {
    Row(Modifier.fillMaxWidth().height(48.dp).glassRow(CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        segments.forEach { segment ->
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .pressScale(interaction)
                    .clip(CircleShape)
                    .background(if (segment.on) Accent else Color.Transparent)
                    .toggleable(segment.on, interactionSource = interaction, indication = null, role = Role.Checkbox, onValueChange = segment.onToggle)
                    .semantics { contentDescription = segment.description },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(segment.icon), contentDescription = null, tint = if (segment.on) OnAccent else TextSecondary)
            }
        }
    }
}
