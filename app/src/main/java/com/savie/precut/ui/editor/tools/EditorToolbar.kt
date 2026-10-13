package com.savie.precut.ui.editor.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.R

/** Every toolbar action; clip tools act on the selected clip, or the one under the playhead. */
interface ToolbarActions {
    fun onSplit()
    fun onTrim()
    fun onSpeed()
    fun onFilters()
    fun onRotate()
    fun onText()
    fun onRatio()
    fun onDuplicate()
    fun onDelete()
}

/** Bottom tool row; scrolls horizontally when the tools don't fit. */
@Composable
fun EditorToolbar(canSplit: Boolean, hasClips: Boolean, actions: ToolbarActions, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            ToolButton(R.drawable.ic_split, R.string.tool_split, canSplit, actions::onSplit)
            ToolButton(R.drawable.ic_trim, R.string.tool_trim, hasClips, actions::onTrim)
            ToolButton(R.drawable.ic_speed, R.string.tool_speed, hasClips, actions::onSpeed)
            ToolButton(R.drawable.ic_filter, R.string.tool_filters, hasClips, actions::onFilters)
            ToolButton(R.drawable.ic_rotate, R.string.tool_rotate, hasClips, actions::onRotate)
            ToolButton(R.drawable.ic_text, R.string.tool_text, hasClips, actions::onText)
            ToolButton(R.drawable.ic_ratio, R.string.tool_ratio, enabled = true, onClick = actions::onRatio)
            ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, hasClips, actions::onDuplicate)
            ToolButton(R.drawable.ic_delete, R.string.tool_delete, hasClips, actions::onDelete)
        }
    }
}

@Composable
private fun ToolButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(64.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
    }
}
