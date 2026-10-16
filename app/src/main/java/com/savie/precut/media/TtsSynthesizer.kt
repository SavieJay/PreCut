package com.savie.precut.media

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.savie.precut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A voice of the phone's text-to-speech engine (X-06). */
data class TtsVoice(
    val name: String,
    /** "English (Nigeria) · Voice 1". */
    val label: String,
    /** Usable offline, or still to be downloaded in Android's settings. */
    val installed: Boolean,
    val needsNetwork: Boolean,
)

/** Turns text into a voiceover file with the device's own (offline) text-to-speech engine. */
@Singleton
class TtsSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private var engine: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /** The engine, started on first use; null when the phone has no usable one. */
    private suspend fun engine(): TextToSpeech? {
        val started = ready ?: CompletableDeferred<Boolean>().also { deferred ->
            ready = deferred
            engine = TextToSpeech(context) { status -> deferred.complete(status == TextToSpeech.SUCCESS) }.apply {
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) = Unit
                    override fun onDone(utteranceId: String) {
                        pending.remove(utteranceId)?.complete(true)
                    }

                    @Deprecated("Superseded by onError(String, Int)", ReplaceWith("onError(utteranceId, 0)"))
                    override fun onError(utteranceId: String) {
                        pending.remove(utteranceId)?.complete(false)
                    }

                    override fun onError(utteranceId: String, errorCode: Int) {
                        pending.remove(utteranceId)?.complete(false)
                    }
                })
            }
        }
        val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) { started.await() } ?: false
        return engine.takeIf { ok }
    }

    /** Installed voices first, grouped by language, numbered within each. */
    suspend fun voices(): List<TtsVoice> {
        val tts = engine() ?: return emptyList()
        fun ready(voice: Voice) = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features && !voice.isNetworkConnectionRequired
        val voices = tts.voices.orEmpty()
            .sortedWith(compareBy<Voice>({ !ready(it) }, { it.locale.displayName }, { it.name }))
        val counts = HashMap<Pair<Boolean, String>, Int>()
        return voices.map { voice ->
            val language = voice.locale.displayName
            val key = ready(voice) to language
            val number = (counts[key] ?: 0) + 1
            counts[key] = number
            TtsVoice(
                name = voice.name,
                label = "$language · $number",
                installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features,
                needsNetwork = voice.isNetworkConnectionRequired,
            )
        }
    }

    /** Speaks [text] once (Preview voice). */
    suspend fun preview(text: String, voiceName: String?, speed: Float, pitch: Float) {
        val tts = engine() ?: return
        configure(tts, voiceName, speed, pitch)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopPreview() {
        engine?.stop()
    }

    /** [text] spoken into a WAV in the voiceover folder; its file Uri, or null if the engine failed. */
    suspend fun synthesize(text: String, voiceName: String?, speed: Float, pitch: Float): String? {
        val tts = engine() ?: return null
        configure(tts, voiceName, speed, pitch)
        val file = withContext(ioDispatcher) {
            File(File(context.filesDir, VOICEOVER_DIR).apply { mkdirs() }, "tts_${UUID.randomUUID()}.wav")
        }
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>().also { pending[id] = it }
        if (tts.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS) {
            pending.remove(id)
            return null
        }
        val ok = withTimeoutOrNull(SYNTH_TIMEOUT_MS) { done.await() } ?: false
        if (!ok || !file.exists() || file.length() == 0L) {
            withContext(ioDispatcher) { file.delete() }
            return null
        }
        return Uri.fromFile(file).toString()
    }

    private fun configure(tts: TextToSpeech, voiceName: String?, speed: Float, pitch: Float) {
        tts.voices.orEmpty().firstOrNull { it.name == voiceName }?.let { tts.voice = it }
        tts.setSpeechRate(speed.coerceIn(0.5f, 2f))
        tts.setPitch(pitch.coerceIn(0.5f, 2f))
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 5_000L
        const val SYNTH_TIMEOUT_MS = 60_000L
        /** The voiceover folder (kept by the unused-media sweep while a project uses the file). */
        const val VOICEOVER_DIR = "voiceovers"
    }
}
