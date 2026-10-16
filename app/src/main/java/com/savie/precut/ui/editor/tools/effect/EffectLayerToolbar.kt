package com.savie.precut.ui.editor.tools.effect

import androidx.compose.runtime.Composable
import com.savie.precut.R
import com.savie.precut.ui.editor.tools.ToolButton
import com.savie.precut.ui.editor.tools.ToolRow

/** What the toolbar of a selected effect layer can do. */
interface EffectLayerActions {
    fun onEffect()
    fun onSplit()
    fun onDuplicate()
    fun onDelete()
}

/** Tools for a selected effect layer. */
@Composable
fun EffectLayerToolbar(canSplit: Boolean, actions: EffectLayerActions) {
    ToolRow {
        ToolButton(R.drawable.ic_flare, R.string.effects_edit, onClick = actions::onEffect)
        ToolButton(R.drawable.ic_split, R.string.tool_split, canSplit, actions::onSplit)
        ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, onClick = actions::onDuplicate)
        ToolButton(R.drawable.ic_delete, R.string.tool_delete, onClick = actions::onDelete)
    }
}
