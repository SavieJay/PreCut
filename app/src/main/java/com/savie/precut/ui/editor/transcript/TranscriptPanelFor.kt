package com.savie.precut.ui.editor.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.ui.editor.EditorViewModel

/** The transcript panel, wired to the ViewModel. */
@Composable
fun TranscriptPanelFor(viewModel: EditorViewModel) {
    val ui by viewModel.transcript.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        object : TranscriptActions {
            override fun onWord(index: Int) = viewModel.transcript.onWord(index)
            override fun onFillers() = viewModel.transcript.onFillers()
            override fun onSilences() = viewModel.transcript.onSilences()
            override fun onPreview() = viewModel.transcript.onPreview()
            override fun onApply() = viewModel.transcript.onApply()
            override fun onMakeCaptions() {
                viewModel.transcript.close()
                viewModel.onCaptions()
            }
            override fun onDone() = viewModel.transcript.close()
        }
    }
    ui?.let { TranscriptPanel(it, actions) }
}
