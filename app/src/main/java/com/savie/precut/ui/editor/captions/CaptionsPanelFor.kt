package com.savie.precut.ui.editor.captions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.model.text.CaptionStyle
import com.savie.precut.model.text.captionAt
import com.savie.precut.model.text.captionsInOrder
import com.savie.precut.model.text.sharedCaptionStyle
import com.savie.precut.model.text.wordAt
import com.savie.precut.ui.editor.EditorUiState
import com.savie.precut.ui.editor.EditorViewModel

/** The Captions panel, wired to the ViewModel. Only a change of caption under the playhead recomposes it during playback. */
@Composable
fun CaptionsPanelFor(state: EditorUiState.Ready, positionMs: () -> Long, viewModel: EditorViewModel) {
    val edits = viewModel.captionEdits
    val editing by edits.editing.collectAsStateWithLifecycle()
    val captions = remember(state.project) { state.project.captionsInOrder() }
    val current by remember(captions, positionMs) { derivedStateOf { captions.captionAt(positionMs())?.id } }
    // Changes only when the spoken word does, so playback recomposes the panel once per word.
    val spokenWord by remember(captions, positionMs) {
        derivedStateOf { positionMs().let { ms -> captions.captionAt(ms)?.let { line -> line.caption?.wordAt(ms - line.startMs) } } }
    }
    val actions = remember(viewModel) {
        object : CaptionsPanelActions {
            override fun onRow(id: String) = edits.onRow(id)
            override fun onText(id: String, text: String) = edits.onText(id, text)
            override fun onNudge(id: String, startDeltaMs: Long, endDeltaMs: Long) = edits.onNudge(id, startDeltaMs, endDeltaMs)
            override fun onSplit(id: String) = edits.onSplit(id)
            override fun onMerge(id: String) = edits.onMerge(id)
            override fun onRegenerate(id: String) = viewModel.captions.onRegenerateLine(id)
            override fun onDelete(id: String) = edits.onDelete(id)
            override fun onRestyle(style: CaptionStyle) = edits.onRestyle(style)
            override fun onGenerateAgain() = viewModel.captions.onOpen()
            override fun onSaveSrt() = edits.onSaveSrt()
            override fun onShareSrt() = edits.onShareSrt()
            override fun onTranslate() = viewModel.translate.open()
            override fun onDone() = viewModel.onToolClosed()
        }
    }
    CaptionsPanel(captions, editing, current, captions.sharedCaptionStyle(), actions, spokenWord)
}
