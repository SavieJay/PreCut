package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.savie.precut.R

/** Every main-toolbar action; clip tools act on the selected clip, or the one under the playhead. */
interface ToolbarActions {
    fun onSplit()
    fun onTrim()
    fun onSpeed()
    fun onFilters()
    fun onAdjust()
    fun onVolume()
    fun onFade()
    fun onAudio()
    fun onExtractAudio()
    fun onRotate()
    fun onReplace()
    fun onText()
    fun onRatio()
    fun onDuplicate()
    fun onDelete()
    fun onSelect()
    fun onCover()
}

/** Main bottom tool row. */
@Composable
fun EditorToolbar(canSplit: Boolean, hasClips: Boolean, actions: ToolbarActions, modifier: Modifier = Modifier) {
    ToolRow(modifier) {
        ToolButton(R.drawable.ic_split, R.string.tool_split, canSplit, actions::onSplit)
        ToolButton(R.drawable.ic_trim, R.string.tool_trim, hasClips, actions::onTrim)
        ToolButton(R.drawable.ic_music, R.string.tool_audio, hasClips, actions::onAudio)
        ToolButton(R.drawable.ic_speed, R.string.tool_speed, hasClips, actions::onSpeed)
        ToolButton(R.drawable.ic_volume, R.string.tool_volume, hasClips, actions::onVolume)
        ToolButton(R.drawable.ic_fade, R.string.tool_fade, hasClips, actions::onFade)
        ToolButton(R.drawable.ic_extract, R.string.tool_extract, hasClips, actions::onExtractAudio)
        ToolButton(R.drawable.ic_filter, R.string.tool_filters, hasClips, actions::onFilters)
        ToolButton(R.drawable.ic_adjust, R.string.tool_adjust, hasClips, actions::onAdjust)
        ToolButton(R.drawable.ic_rotate, R.string.tool_rotate, hasClips, actions::onRotate)
        ToolButton(R.drawable.ic_replace, R.string.audio_replace, hasClips, actions::onReplace)
        ToolButton(R.drawable.ic_text, R.string.tool_text, hasClips, actions::onText)
        ToolButton(R.drawable.ic_ratio, R.string.tool_ratio, onClick = actions::onRatio)
        ToolButton(R.drawable.ic_cover, R.string.tool_cover, hasClips, actions::onCover)
        ToolButton(R.drawable.ic_select, R.string.tool_select, hasClips, actions::onSelect)
        ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, hasClips, actions::onDuplicate)
        ToolButton(R.drawable.ic_delete, R.string.tool_delete, hasClips, actions::onDelete)
    }
}
