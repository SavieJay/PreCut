package com.savie.precut.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary

enum class HomeTab { DRAFTS, TEMPLATES }

private val TabDivider = Color.White.copy(alpha = 0.08f)
private val IndicatorShape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)

/** Drafts | Templates, as the 2026 underline tabs (RD-CMP `.tabs`): 44 dp, a 3 dp pink bar under the current one. */
@Composable
fun HomeTabs(tab: HomeTab, onTab: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(TabDivider))
        Row(Modifier.padding(horizontal = 20.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            HomeTab.entries.forEach { entry ->
                val selected = entry == tab
                Column(
                    Modifier.width(IntrinsicSize.Max).height(48.dp).selectable(selected = selected, role = Role.Tab, onClick = { onTab(entry) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (entry == HomeTab.DRAFTS) R.string.home_tab_drafts else R.string.home_tab_templates),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) TextPrimary else TextMuted,
                        )
                    }
                    Box(Modifier.matchParentWidthBar(selected))
                }
            }
        }
    }
}

private fun Modifier.matchParentWidthBar(selected: Boolean): Modifier =
    fillMaxWidth().height(3.dp).then(if (selected) Modifier.background(Accent, IndicatorShape) else Modifier)
