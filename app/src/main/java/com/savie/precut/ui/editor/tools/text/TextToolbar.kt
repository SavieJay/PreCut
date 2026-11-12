package com.savie.precut.ui.editor.tools.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.savie.precut.R
import com.savie.precut.ui.editor.tools.AiToolButton
import com.savie.precut.ui.editor.tools.ToolButton
import com.savie.precut.ui.editor.tools.ToolDivider
import com.savie.precut.ui.editor.tools.ToolRow

/** What the selected text's toolbar can do; each is an action the text panels already have. */
interface TextToolbarActions {
    /** The typing panel (X-01). */
    fun onEdit()
    /** The style panel (X-04) on [tab]: 0 Font, 1 Style, 2 Effects, 3 Animation, 4 Templates. */
    fun onStyle(tab: Int)
    fun onRich()
    fun onTts()
    fun onCaptions()
    /** Follow person (Phase 26A). */
    fun onFollow()
    fun onSplit()
    fun onDuplicate()
    fun onDelete()
}

/**
 * RD-X02 (Phase 29E): the dock for a selected text. Tapping a text bar shows this instead of the
 * typing panel, so the whole timeline stays and the bar can be dragged and trimmed.
 */
@Composable
fun TextToolbar(canSplit: Boolean, actions: TextToolbarActions, modifier: Modifier = Modifier) {
    ToolRow(modifier) {
        ToolButton(R.drawable.ic_edit, R.string.tool_edit, onClick = actions::onEdit)
        ToolButton(R.drawable.ic_font_download, R.string.text_tab_font) { actions.onStyle(0) }
        ToolButton(R.drawable.ic_style, R.string.text_tab_style) { actions.onStyle(1) }
        ToolButton(R.drawable.ic_flare, R.string.text_tab_effects) { actions.onStyle(2) }
        ToolButton(R.drawable.ic_animation, R.string.text_tab_animation) { actions.onStyle(3) }
        ToolButton(R.drawable.ic_format_bold, R.string.tool_rich_text, onClick = actions::onRich)
        ToolButton(R.drawable.ic_record_voice_over, R.string.tool_speech, onClick = actions::onTts)
        AiToolButton(R.drawable.ic_closed_caption, R.string.tool_captions, onClick = actions::onCaptions)
        ToolDivider()
        ToolButton(R.drawable.ic_frame_person, R.string.tool_follow, onClick = actions::onFollow)
        ToolButton(R.drawable.ic_split, R.string.tool_split, canSplit, actions::onSplit)
        ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, onClick = actions::onDuplicate)
        ToolButton(R.drawable.ic_delete, R.string.tool_delete, onClick = actions::onDelete)
    }
}
