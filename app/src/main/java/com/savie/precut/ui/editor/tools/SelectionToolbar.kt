package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.PreCutType
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
        ToolButton(R.drawable.ic_check, R.string.action_done, onClick = actions::onDone)
        Text(
            LocalResources.current.getQuantityString(R.plurals.selection_count, count, count),
            style = PreCutType.labelChip.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp),
        )
        Box(Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp).size(width = 1.dp, height = 32.dp).background(Ink3))
        ToolButton(R.drawable.ic_delete, R.string.tool_delete, any, actions::onDelete)
        ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, any, actions::onDuplicate)
        ToolButton(R.drawable.ic_speed, R.string.tool_speed, hasClips, actions::onSpeed)
        ToolButton(R.drawable.ic_filter, R.string.tool_filters, hasClips, actions::onFilter)
        ToolButton(R.drawable.ic_volume, R.string.tool_volume, any, actions::onVolume)
    }
}
