package com.savie.precut.ui.editor.captions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.CaptionStyle
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.editor.tools.ToolPanel

/** What the Captions panel can do. */
interface CaptionsPanelActions {
    fun onRow(id: String)
    fun onText(id: String, text: String)
    fun onNudge(id: String, startDeltaMs: Long, endDeltaMs: Long)
    fun onSplit(id: String)
    fun onMerge(id: String)
    fun onRegenerate(id: String)
    fun onDelete(id: String)
    fun onRestyle(style: CaptionStyle)
    fun onGenerateAgain()
    fun onSaveSrt()
    fun onShareSrt()
    /** Translate (and dub) every caption (Phase 26B, Pro). */
    fun onTranslate()
    fun onDone()
}

/**
 * The Captions panel (Phase 21B): a style for every caption, the lines in time order (the one
 * under the playhead marked and kept in view; one open for editing), Generate again, and the SRT
 * file from the header.
 */
@Composable
fun CaptionsPanel(
    /** The captions, in time order. */
    captions: List<TextOverlay>,
    editingId: String?,
    /** The caption under the playhead. */
    currentId: String?,
    /** The style every caption has, if they share one. */
    style: CaptionStyle?,
    actions: CaptionsPanelActions,
) {
    val list = rememberLazyListState()
    LaunchedEffect(editingId ?: currentId) {
        val index = captions.indexOfFirst { it.id == (editingId ?: currentId) }
        if (index >= 0) list.animateScrollToItem(index)
    }
    ToolPanel(
        stringResource(R.string.tool_captions),
        actions::onDone,
        actions = {
            IconButton(onClick = actions::onTranslate) {
                Icon(painterResource(R.drawable.ic_record_voice_over), contentDescription = stringResource(R.string.translate_title), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = actions::onSaveSrt) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = stringResource(R.string.captions_save_srt), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = actions::onShareSrt) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.captions_share_srt), modifier = Modifier.size(20.dp))
            }
        },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptionStyle.entries.forEach { preset ->
                PreCutChip(stringResource(preset.label()), selected = style == preset, onClick = { actions.onRestyle(preset) })
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(captions, key = { _, caption -> caption.id }) { index, caption ->
                CaptionRow(
                    caption = caption,
                    current = caption.id == currentId,
                    editing = caption.id == editingId,
                    canMerge = index < captions.lastIndex,
                    actions = actions,
                )
            }
        }
        TonalButton(stringResource(R.string.captions_generate_again), actions::onGenerateAgain, Modifier.fillMaxWidth().height(44.dp), icon = R.drawable.ic_closed_caption)
    }
}
