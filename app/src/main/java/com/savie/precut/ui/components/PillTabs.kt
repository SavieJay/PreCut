package com.savie.precut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.TextFaint
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glassRow

/** One segment: [badge] is a small note after the label (e.g. "Soon"). */
data class PillTab(val label: String, val enabled: Boolean = true, val badge: String? = null)

/**
 * The 2026 segmented control (RD-TOK `.seg`): a 48 dp quiet glass track with 4 dp padding; the
 * selected segment is pink with a dark label. Pass a fill-width [modifier] and segments share it.
 */
@Composable
fun PillTabs(tabs: List<PillTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, fillWidth: Boolean = false) {
    Row(
        modifier.height(48.dp).glassRow(CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val on = index == selected
            val interaction = remember { MutableInteractionSource() }
            val color = when {
                !tab.enabled -> TextFaint
                on -> OnAccent
                else -> TextSecondary
            }
            Row(
                Modifier
                    .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                    .height(40.dp)
                    .pressScale(interaction)
                    .clip(CircleShape)
                    .background(if (on) Accent else Accent.copy(alpha = 0f))
                    .selectable(on, enabled = tab.enabled, role = Role.Tab, interactionSource = interaction, indication = null) { onSelect(index) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                Text(tab.label, style = ChipText, color = color, maxLines = 1)
                tab.badge?.let { Text(it, style = ChipText.copy(fontSize = ChipText.fontSize * 0.77f), color = if (on) OnAccent else TextSecondary) }
            }
        }
    }
}
