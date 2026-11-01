package com.savie.precut.ui.editor

import com.savie.precut.ai.cloud.CaptionLineDto
import com.savie.precut.ai.cloud.CloudClient
import com.savie.precut.media.MediaImporter
import com.savie.precut.media.TtsSynthesizer
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.pro.ProFeature
import com.savie.precut.model.text.voiceoverClip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Languages offered for translation, by code; the sheet names them. */
val TRANSLATE_LANGUAGES = listOf("en", "fr", "es", "pt", "de", "ar", "yo", "ig", "ha", "sw", "hi", "zh")

/** The translate sheet. */
sealed interface TranslateUi {
    data class Choose(val language: String = "en", val dub: Boolean = false, val failure: AiFailure? = null) : TranslateUi
    data class Working(val dubbing: Boolean) : TranslateUi
}

/**
 * Translate captions, and optionally dub them (Phase 26B, Pro): the captions' text goes to
 * PreCut's server and Claude (the sheet says so); the dub is spoken on the phone by its own
 * text-to-speech voices, never a copy of anyone's voice. One undo step: the new words, and with a
 * dub the voiceovers at each line and the clips' own sound muted.
 */
class EditorTranslate(
    private val host: EditorHost,
    private val pro: EditorPro,
    private val cloud: CloudClient,
    private val tts: TtsSynthesizer,
    private val importer: MediaImporter,
) {
    private val _state = MutableStateFlow<TranslateUi?>(null)
    val state: StateFlow<TranslateUi?> = _state.asStateFlow()

    fun open() = pro.gate(ProFeature.TRANSLATE_CAPTIONS) { _state.value = TranslateUi.Choose() }

    fun onLanguage(code: String) = _state.update { (it as? TranslateUi.Choose)?.copy(language = code, failure = null) ?: it }

    fun onDub(dub: Boolean) = _state.update { (it as? TranslateUi.Choose)?.copy(dub = dub) ?: it }

    fun onDismiss() {
        if (_state.value !is TranslateUi.Working) _state.value = null
    }

    fun onTranslate() {
        val choice = _state.value as? TranslateUi.Choose ?: return
        val project = host.ready?.project ?: return
        val captions = project.textOverlays.filter { it.caption != null && it.text.isNotBlank() }
        if (captions.isEmpty()) return
        _state.value = TranslateUi.Working(choice.dub)
        host.scope.launch {
            val lines = captions.map { CaptionLineDto(it.id, it.text) }
            when (val result = cloud.translate(lines, choice.language)) {
                is AiResult.Failed -> _state.value = choice.copy(failure = result.failure)
                is AiResult.Ok -> {
                    pro.spend(ProFeature.TRANSLATE_CAPTIONS)
                    val texts = result.value.associate { it.id to it.text }
                    val edits = mutableListOf<EditAction>()
                    captions.forEach { caption -> texts[caption.id]?.takeIf { it != caption.text }?.let { edits += EditAction.SetCaptionText(caption.id, it) } }
                    var dubbed = 0
                    if (choice.dub) {
                        val voice = tts.voices().firstOrNull { it.installed && !it.needsNetwork && it.language.take(2) == choice.language.take(2) }
                        if (voice != null) {
                            captions.forEach { caption ->
                                val words = texts[caption.id] ?: return@forEach
                                val uri = tts.synthesize(words, voice.name, 1f, 1f) ?: return@forEach
                                val duration = importer.importAudio(uri)?.durationMs ?: return@forEach
                                edits += EditAction.AddAudio(caption.voiceoverClip(host.newId(), uri, duration))
                                dubbed++
                            }
                            if (dubbed > 0) edits += EditAction.SetOriginalAudioMuted(true)
                        }
                    }
                    if (edits.isNotEmpty()) host.applyEdit(EditAction.Batch(edits))
                    _state.value = null
                    host.send(EditorEvent.Translated(dubbed = dubbed, noVoice = choice.dub && dubbed == 0))
                }
            }
        }
    }
}
