package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.editor.tools.text.RichTextTool
import com.savie.precut.ui.editor.tools.text.TextStyleActions
import com.savie.precut.ui.editor.tools.text.TextStyleTool
import com.savie.precut.ui.editor.tools.text.TtsActions
import com.savie.precut.ui.editor.tools.text.TtsTool

/** The panels opened from the Text panel's Style, Rich text and Text to speech buttons. */
val TEXT_STYLE_TOOLS = setOf(EditorTool.TEXT_STYLE, EditorTool.TEXT_RICH, EditorTool.TEXT_TTS)

@Composable
fun TextStylePanels(state: EditorUiState.Ready, text: TextOverlay, tool: EditorTool, viewModel: EditorViewModel) {
    val controller = viewModel.textStyle
    val close = viewModel::onToolClosed
    val recentColors = remember(state.project.textOverlays) { state.project.textOverlays.map { it.colorArgb }.distinct() }
    when (tool) {
        EditorTool.TEXT_STYLE -> {
            val fonts by controller.importedFonts.collectAsStateWithLifecycle()
            val actions = remember(controller) {
                object : TextStyleActions {
                    override fun onChange(overlay: TextOverlay) = controller.onChange(overlay)
                    override fun onImportFont() = controller.onImportFont()
                    override fun onApplyAll() = controller.onApplyAll()
                    override fun onBack() = controller.onBack()
                }
            }
            TextStyleTool(text, fonts, recentColors, viewModel::renderText, actions, onDone = close)
        }
        EditorTool.TEXT_RICH -> RichTextTool(text, recentColors, controller::onChange, controller::onBack, onDone = close)
        EditorTool.TEXT_TTS -> {
            val voices by controller.voices.collectAsStateWithLifecycle()
            val voice by controller.voice.collectAsStateWithLifecycle()
            val making by controller.making.collectAsStateWithLifecycle()
            val actions = remember(controller) {
                object : TtsActions {
                    override fun onVoice(name: String) = controller.onVoice(name)
                    override fun onPreview(speed: Float, pitch: Float) = controller.onPreview(speed, pitch)
                    override fun onAdd(speed: Float, pitch: Float) = controller.onAddVoiceover(speed, pitch)
                    override fun onBack() = controller.onBack()
                }
            }
            TtsTool(voices, voice, making, actions, onDone = close)
        }
        else -> Unit
    }
}
