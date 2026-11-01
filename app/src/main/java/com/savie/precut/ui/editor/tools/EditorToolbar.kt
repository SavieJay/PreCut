package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    fun onEdit()
    fun onCanvas()
    fun onOpacity()
    fun onMotionBlur()
    fun onMask()
    fun onChroma()
    fun onCutout()
    fun onReframe()
    fun onAnimation()
    fun onFreeze()
    fun onReverse()
    fun onOverlay()
    fun onToOverlay()
    fun onReplace()
    fun onText()
    fun onCaptions()
    fun onAiEdit()
    fun onStickers()
    fun onDraw()
    fun onEffects()
    fun onRatio()
    fun onDuplicate()
    fun onDelete()
    fun onSelect()
    fun onCover()
}

/**
 * Main bottom tool row. In [simple] mode (Phase 24) it shows the everyday tools and More, which
 * opens the full row.
 */
@Composable
fun EditorToolbar(canSplit: Boolean, hasClips: Boolean, actions: ToolbarActions, modifier: Modifier = Modifier, simple: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ToolRow(modifier) {
        if (simple && !expanded) {
            ToolButton(R.drawable.ic_auto_awesome, R.string.tool_ai_edit, hasClips, actions::onAiEdit)
            ToolButton(R.drawable.ic_split, R.string.tool_split, canSplit, actions::onSplit)
            ToolButton(R.drawable.ic_trim, R.string.tool_trim, hasClips, actions::onTrim)
            ToolButton(R.drawable.ic_music, R.string.tool_audio, hasClips, actions::onAudio)
            ToolButton(R.drawable.ic_text, R.string.tool_text, hasClips, actions::onText)
            ToolButton(R.drawable.ic_filter, R.string.tool_filters, hasClips, actions::onFilters)
            ToolButton(R.drawable.ic_apps, R.string.tool_more, onClick = { expanded = true })
        } else {
            AllTools(canSplit, hasClips, actions)
            if (simple) ToolButton(R.drawable.ic_apps, R.string.tool_less, onClick = { expanded = false })
        }
    }
}

@Composable
private fun RowScope.AllTools(canSplit: Boolean, hasClips: Boolean, actions: ToolbarActions) {
        ToolButton(R.drawable.ic_auto_awesome, R.string.tool_ai_edit, hasClips, actions::onAiEdit)
        ToolButton(R.drawable.ic_split, R.string.tool_split, canSplit, actions::onSplit)
        ToolButton(R.drawable.ic_crop, R.string.tool_edit, hasClips, actions::onEdit)
        ToolButton(R.drawable.ic_trim, R.string.tool_trim, hasClips, actions::onTrim)
        ToolButton(R.drawable.ic_music, R.string.tool_audio, hasClips, actions::onAudio)
        ToolButton(R.drawable.ic_speed, R.string.tool_speed, hasClips, actions::onSpeed)
        ToolButton(R.drawable.ic_animation, R.string.tool_animation, hasClips, actions::onAnimation)
        ToolButton(R.drawable.ic_canvas, R.string.tool_canvas, hasClips, actions::onCanvas)
        ToolButton(R.drawable.ic_opacity, R.string.tool_opacity, hasClips, actions::onOpacity)
        ToolButton(R.drawable.ic_motion_blur, R.string.tool_motion_blur, hasClips, actions::onMotionBlur)
        ToolButton(R.drawable.ic_masks, R.string.tool_mask, hasClips, actions::onMask)
        ToolButton(R.drawable.ic_background_replace, R.string.tool_chroma, hasClips, actions::onChroma)
        ToolButton(R.drawable.ic_frame_person, R.string.tool_cutout, hasClips, actions::onCutout)
        ToolButton(R.drawable.ic_reframe, R.string.tool_reframe, hasClips, actions::onReframe)
        ToolButton(R.drawable.ic_freeze, R.string.tool_freeze, hasClips, actions::onFreeze)
        ToolButton(R.drawable.ic_reverse, R.string.tool_reverse, hasClips, actions::onReverse)
        ToolButton(R.drawable.ic_volume, R.string.tool_volume, hasClips, actions::onVolume)
        ToolButton(R.drawable.ic_fade, R.string.tool_fade, hasClips, actions::onFade)
        ToolButton(R.drawable.ic_extract, R.string.tool_extract, hasClips, actions::onExtractAudio)
        ToolButton(R.drawable.ic_filter, R.string.tool_filters, hasClips, actions::onFilters)
        ToolButton(R.drawable.ic_adjust, R.string.tool_adjust, hasClips, actions::onAdjust)
        ToolButton(R.drawable.ic_replace, R.string.audio_replace, hasClips, actions::onReplace)
        ToolButton(R.drawable.ic_flare, R.string.tool_effects, hasClips, actions::onEffects)
        ToolButton(R.drawable.ic_text, R.string.tool_text, hasClips, actions::onText)
        ToolButton(R.drawable.ic_closed_caption, R.string.tool_captions, hasClips, actions::onCaptions)
        ToolButton(R.drawable.ic_add_reaction, R.string.tool_stickers, hasClips, actions::onStickers)
        ToolButton(R.drawable.ic_draw, R.string.tool_draw, hasClips, actions::onDraw)
        ToolButton(R.drawable.ic_overlay, R.string.tool_overlay, hasClips, actions::onOverlay)
        ToolButton(R.drawable.ic_swap_track, R.string.tool_to_overlay, hasClips, actions::onToOverlay)
        ToolButton(R.drawable.ic_ratio, R.string.tool_ratio, onClick = actions::onRatio)
        ToolButton(R.drawable.ic_cover, R.string.tool_cover, hasClips, actions::onCover)
        ToolButton(R.drawable.ic_select, R.string.tool_select, hasClips, actions::onSelect)
        ToolButton(R.drawable.ic_duplicate, R.string.tool_duplicate, hasClips, actions::onDuplicate)
        ToolButton(R.drawable.ic_delete, R.string.tool_delete, hasClips, actions::onDelete)
}
