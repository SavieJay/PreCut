package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.savie.precut.R

/** What the overlay toolbar can do with the selected overlay. */
interface OverlayToolbarActions {
    fun onAnimation()
    fun onOpacity()
    fun onFilters()
    fun onAdjust()
    fun onToMain()
    fun onSplit()
    fun onDuplicate()
    fun onDelete()
}

/** P-02: tools for a selected overlay (Mask and Chroma key arrive in Phase 9B). */
@Composable
fun OverlayToolbar(actions: OverlayToolbarActions, modifier: Modifier = Modifier) {
    ToolRow(modifier) {
        ToolButton(R.drawable.ic_animation, R.string.tool_animation, onClick = actions::onAnimation)
        ToolButton(R.drawable.ic_opacity, R.string.tool_opacity, onClick = actions::onOpacity)
        ToolButton(R.drawable.ic_filter, R.string.tool_filters, onClick = actions::onFilters)
        ToolButton(R.drawable.ic_adjust, R.string.tool_adjust, onClick = actions::onAdjust)
        ToolButton(R.drawable.ic_swap_track, R.string.tool_to_main, onClick = actions::onToMain)
        ToolButton(R.drawable.ic_split, R.string.tool_split, onClick = actions::onSplit)
        ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, onClick = actions::onDuplicate)
        ToolButton(R.drawable.ic_delete, R.string.tool_delete, onClick = actions::onDelete)
    }
}
