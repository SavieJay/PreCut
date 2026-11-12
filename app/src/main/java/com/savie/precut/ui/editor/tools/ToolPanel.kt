package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.DonePill
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.glass

/**
 * Shell shared by every tool panel (replaces the dock while open; RD-TOK "Panel"): a glass panel
 * inset 8 dp with 28 dp corners, a 60 dp header (title, optional [actions], the round Done), then
 * the body with 12 dp between rows.
 */
@Composable
fun ToolPanel(
    title: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Extra header buttons, before Done (e.g. "Apply to all"). */
    actions: @Composable RowScope.() -> Unit = {},
    /** A back arrow before the title (HSL and Curves return to Adjust). */
    onBack: (() -> Unit)? = null,
    /** The back button's icon and label (Draw shows a Close that discards). */
    backIcon: Int = R.drawable.ic_back,
    backLabel: Int = R.string.action_back,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .glass(Radius.panel),
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            onBack?.let { back -> PreCutIconButton(backIcon, stringResource(backLabel), back) }
            Text(
                title,
                style = PreCutType.panelTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
            DonePill(onClick = onDone)
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
