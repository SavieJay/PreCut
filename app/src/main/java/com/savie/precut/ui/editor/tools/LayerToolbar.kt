package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.savie.precut.R

/** What can be done with a selected filter/adjust layer. */
interface LayerToolbarActions {
    fun onFilter()
    fun onAdjust()
    fun onSplit()
    fun onDuplicate()
    fun onDelete()
}

/** Toolbar shown while a look layer is selected on the timeline. */
@Composable
fun LayerToolbar(canSplit: Boolean, actions: LayerToolbarActions, modifier: Modifier = Modifier) {
    ToolRow(modifier) {
        ToolButton(R.drawable.ic_filter, R.string.tool_filters, onClick = actions::onFilter)
        ToolButton(R.drawable.ic_adjust, R.string.tool_adjust, onClick = actions::onAdjust)
        ToolButton(R.drawable.ic_split, R.string.tool_split, canSplit, actions::onSplit)
        ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, onClick = actions::onDuplicate)
        ToolButton(R.drawable.ic_delete, R.string.tool_delete, onClick = actions::onDelete)
    }
}
