package com.savie.precut.ui.editor

import android.net.Uri
import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.MediaImporter
import com.savie.precut.media.TtsSynthesizer
import com.savie.precut.media.TtsVoice
import com.savie.precut.media.text.ImportedFont
import com.savie.precut.media.text.TextFonts
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.edit.BatchEdits
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.text.voiceoverClip
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Text style (X-04), rich text (X-05) and text to speech (X-06) for the selected text. */
class EditorTextStyle(
    private val host: EditorHost,
    private val fonts: TextFonts,
    private val tts: TtsSynthesizer,
    private val importer: MediaImporter,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _importedFonts = MutableStateFlow<List<ImportedFont>>(emptyList())
    val importedFonts: StateFlow<List<ImportedFont>> = _importedFonts.asStateFlow()

    /** Null while the engine is starting. */
    private val _voices = MutableStateFlow<List<TtsVoice>?>(null)
    val voices: StateFlow<List<TtsVoice>?> = _voices.asStateFlow()

    private val _voice = MutableStateFlow<String?>(null)
    val voice: StateFlow<String?> = _voice.asStateFlow()

    private val _making = MutableStateFlow(false)
    val making: StateFlow<Boolean> = _making.asStateFlow()

    /** Opens TEXT_STYLE, TEXT_RICH or TEXT_TTS on the selected text. */
    fun open(tool: EditorTool) {
        if (host.ready?.selectedTextId == null) return
        host.update { it.copy(activeTool = tool) }
        when (tool) {
            EditorTool.TEXT_STYLE -> host.scope.launch { _importedFonts.value = withContext(io) { fonts.imported() } }
            EditorTool.TEXT_TTS -> if (_voices.value.isNullOrEmpty()) {
                host.scope.launch {
                    val found = tts.voices()
                    _voices.value = found
                    if (_voice.value == null) _voice.value = found.firstOrNull { it.installed }?.name
                }
            }
            else -> Unit
        }
    }

    /** Back to the Text panel. */
    fun onBack() {
        tts.stopPreview()
        host.update { it.copy(activeTool = EditorTool.TEXT) }
    }

    fun onChange(overlay: TextOverlay) = host.applyEdit(EditAction.UpdateText(overlay))

    fun onApplyAll() {
        val ready = host.ready ?: return
        val text = ready.project.textOverlays.firstOrNull { it.id == ready.selectedTextId } ?: return
        host.applyEdit(BatchEdits.textStyle(ready.project, text))
    }

    fun onImportFont() = host.send(EditorEvent.PickFont)

    fun onFontPicked(uri: String?) {
        if (uri == null) return
        host.scope.launch {
            val id = withContext(io) { fonts.import(Uri.parse(uri)) }
            if (id == null) {
                host.send(EditorEvent.FontNotUsable)
                return@launch
            }
            _importedFonts.value = withContext(io) { fonts.imported() }
            selected()?.let { onChange(it.copy(fontId = id)) }
        }
    }

    // Text to speech

    fun onVoice(name: String) {
        _voice.value = name
    }

    fun onPreview(speed: Float, pitch: Float) {
        val text = selected()?.text?.takeIf { it.isNotBlank() } ?: return
        host.scope.launch { tts.preview(text, _voice.value, speed, pitch) }
    }

    /** Speaks the text into a voiceover at the text's start (X-06). */
    fun onAddVoiceover(speed: Float, pitch: Float) {
        val text = selected()?.takeIf { it.text.isNotBlank() } ?: return
        if (_making.value) return
        _making.value = true
        host.scope.launch {
            try {
                tts.stopPreview()
                val uri = tts.synthesize(text.text, _voice.value, speed, pitch)
                val duration = uri?.let { importer.importAudio(it) }?.durationMs
                if (uri == null || duration == null) {
                    host.send(EditorEvent.SpeechFailed)
                    return@launch
                }
                host.applyEdit(EditAction.AddAudio(text.voiceoverClip(host.newId(), uri, duration)))
                onBack()
            } finally {
                _making.value = false
            }
        }
    }

    private fun selected(): TextOverlay? {
        val ready = host.ready ?: return null
        return ready.project.textOverlays.firstOrNull { it.id == ready.selectedTextId }
    }
}
