package com.savie.precut.ui.editor.tools.sticker

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.savie.precut.R
import com.savie.precut.ui.editor.tools.ToolButton
import com.savie.precut.ui.editor.tools.ToolRow

/** What the sticker toolbar can do with the selected sticker. */
interface StickerToolbarActions {
    fun onAnimation()
    fun onOpacity()
    fun onKeyframe()
    fun onSplit()
    fun onDuplicate()
    fun onDelete()
}

/** S-01b: tools for a selected sticker. */
@Composable
fun StickerToolbar(canSplit: Boolean, actions: StickerToolbarActions, modifier: Modifier = Modifier) {
    ToolRow(modifier) {
        ToolButton(R.drawable.ic_animation, R.string.tool_animation, onClick = actions::onAnimation)
        ToolButton(R.drawable.ic_opacity, R.string.tool_opacity, onClick = actions::onOpacity)
        ToolButton(R.drawable.ic_diamond, R.string.tool_keyframe, onClick = actions::onKeyframe)
        ToolButton(R.drawable.ic_split, R.string.tool_split, canSplit, actions::onSplit)
        ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, onClick = actions::onDuplicate)
        ToolButton(R.drawable.ic_delete, R.string.tool_delete, onClick = actions::onDelete)
    }
}
