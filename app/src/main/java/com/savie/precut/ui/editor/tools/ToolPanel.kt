package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.DonePill
import com.savie.precut.ui.theme.Ink1

/**
 * Shell shared by every tool panel (replaces the toolbar while open): Ink1 with 16 dp top corners,
 * a 52 dp header (title, optional [actions], Done pill), then the body with 10 dp between rows.
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Ink1),
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = if (onBack != null) 4.dp else 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            onBack?.let { back ->
                IconButton(onClick = back) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.adjust_back))
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
            Spacer(Modifier.width(4.dp))
            DonePill(onClick = onDone)
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
