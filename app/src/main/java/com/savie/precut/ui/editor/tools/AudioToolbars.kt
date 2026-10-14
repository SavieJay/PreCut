package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.savie.precut.R

/** CapCut's Audio menu: add music from the device, extract a clip's sound, record a voiceover. */
@Composable
fun AudioMenuBar(
    onBack: () -> Unit,
    onMusic: () -> Unit,
    onExtract: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolRow(modifier) {
        ToolButton(R.drawable.ic_back, R.string.action_back, onClick = onBack)
        ToolButton(R.drawable.ic_queue_music, R.string.audio_menu_music, onClick = onMusic)
        ToolButton(R.drawable.ic_extract, R.string.tool_extract, onClick = onExtract)
        ToolButton(R.drawable.ic_mic, R.string.audio_menu_record, onClick = onRecord)
    }
}

/** Actions on the selected audio clip. */
interface AudioToolbarActions {
    fun onVolume()
    fun onFade()
    fun onSplit()
    fun onSpeed()
    fun onVoice()
    fun onBeats()
    fun onReplace()
    fun onDuplicate()
    fun onDelete()
}

/** Toolbar shown while an audio clip is selected. */
@Composable
fun AudioToolbar(canSplit: Boolean, actions: AudioToolbarActions, modifier: Modifier = Modifier) {
    ToolRow(modifier) {
        ToolButton(R.drawable.ic_volume, R.string.tool_volume, onClick = actions::onVolume)
        ToolButton(R.drawable.ic_fade, R.string.tool_fade, onClick = actions::onFade)
        ToolButton(R.drawable.ic_split, R.string.tool_split, canSplit, actions::onSplit)
        ToolButton(R.drawable.ic_speed, R.string.tool_speed, onClick = actions::onSpeed)
        ToolButton(R.drawable.ic_voice, R.string.tool_voice, onClick = actions::onVoice)
        ToolButton(R.drawable.ic_beats, R.string.tool_beats, onClick = actions::onBeats)
        ToolButton(R.drawable.ic_replace, R.string.audio_replace, onClick = actions::onReplace)
        ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, onClick = actions::onDuplicate)
        ToolButton(R.drawable.ic_delete, R.string.tool_delete, onClick = actions::onDelete)
    }
}
