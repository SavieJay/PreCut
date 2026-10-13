package com.savie.precut.ui.editor.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/** Bottom tool row. Clip tools act on the selected clip, or the one under the playhead. */
@Composable
fun EditorToolbar(
    canSplit: Boolean,
    hasClips: Boolean,
    onSplit: () -> Unit,
    onTrim: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolButton(R.drawable.ic_split, R.string.tool_split, enabled = canSplit, onClick = onSplit)
            ToolButton(R.drawable.ic_trim, R.string.tool_trim, enabled = hasClips, onClick = onTrim)
            ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, enabled = hasClips, onClick = onDuplicate)
            ToolButton(R.drawable.ic_delete, R.string.tool_delete, enabled = hasClips, onClick = onDelete)
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
