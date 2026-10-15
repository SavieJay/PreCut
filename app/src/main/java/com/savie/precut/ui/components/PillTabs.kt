package com.savie.precut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Ink4
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** One pill tab: [badge] is a small note after the label (e.g. "Soon"). */
data class PillTab(val label: String, val enabled: Boolean = true, val badge: String? = null)

/** Pill tabs on an Ink2 track (3 dp padding); the selected tab is Ink4. */
@Composable
fun PillTabs(tabs: List<PillTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(Ink2).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val on = index == selected
            Row(
                Modifier
                    .height(30.dp)
                    .clip(CircleShape)
                    .background(if (on) Ink4 else Ink2)
                    .selectable(on, enabled = tab.enabled, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    tab.label,
                    style = PreCutType.labelChip,
                    color = when {
                        !tab.enabled -> TextDisabled
                        on -> TextPrimary
                        else -> TextSecondary
                    },
                )
                tab.badge?.let { Text(it, style = PreCutType.labelChip.copy(fontSize = PreCutType.labelChip.fontSize * 0.77f), color = TextSecondary) }
            }
        }
    }
}
