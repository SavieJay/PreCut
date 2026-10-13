package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import com.savie.precut.R

/** What the multi-select toolbar can do with the picked items. */
interface SelectionActions {
    fun onDone()
    fun onDelete()
    fun onDuplicate()
    fun onSpeed()
    fun onFilter()
    fun onVolume()
}

/** Toolbar in multi-select mode: the count, then edits applied to every picked item at once. */
@Composable
fun SelectionToolbar(count: Int, hasClips: Boolean, actions: SelectionActions, modifier: Modifier = Modifier) {
    val any = count > 0
    ToolRow(modifier) {
        ToolButton(R.drawable.ic_back, R.string.action_done, onClick = actions::onDone)
        Text(
            LocalResources.current.getQuantityString(R.plurals.selection_count, count, count),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp),
        )
        ToolButton(R.drawable.ic_delete, R.string.tool_delete, any, actions::onDelete)
        ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, any, actions::onDuplicate)
        ToolButton(R.drawable.ic_speed, R.string.tool_speed, hasClips, actions::onSpeed)
        ToolButton(R.drawable.ic_filter, R.string.tool_filters, hasClips, actions::onFilter)
        ToolButton(R.drawable.ic_volume, R.string.tool_volume, any, actions::onVolume)
    }
}
