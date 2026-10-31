package com.savie.precut.ai

import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.ExportState
import com.savie.precut.media.SpeechAudio
import com.savie.precut.media.VideoExporter
import com.savie.precut.model.Project
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiFeature
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.ai.SpeechPart
import com.savie.precut.model.ai.SpeechParts
import com.savie.precut.model.ai.TranscriptWord
import com.savie.precut.model.ai.UploadSummary
import com.savie.precut.model.text.withoutMusic
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** One Generate's sound, exported and cut into parts, waiting for the user's consent. */
class PreparedSpeech(
    /** The parts in order, each with its M4A file. */
    val parts: List<Pair<SpeechPart, File>>,
    /** The whole timeline; parts may cover less when the free minutes don't reach. */
    val durationMs: Long,
) {
    /** What the consent dialog shows: every part's length and size. */
    val summary: UploadSummary
        get() = UploadSummary(
            AiFeature.CAPTIONS,
            durationMs = parts.sumOf { it.first.durationMs },
            bytes = parts.sumOf { it.second.length() },
            partBytes = if (parts.size > 1) parts.map { it.second.length() } else emptyList(),
        )

    /** Deletes the files; the job is over or was cancelled. */
    fun discard() = parts.forEach { it.second.delete() }
}

sealed interface SpeechPrep {
    data class Ready(val speech: PreparedSpeech) : SpeechPrep

    /** The sound couldn't be exported or read. */
    data object Failed : SpeechPrep

    /** The free minutes left don't cover even the first part. */
    data object NoMinutes : SpeechPrep
}

/** What the uploads gave: the words heard (timeline time) up to [coveredMs]; [failure] or [stopped] says why not further. */
data class HeardSpeech(
    val words: List<TranscriptWord>,
    val language: String?,
    val coveredMs: Long,
    val failure: AiFailure? = null,
    val stopped: Boolean = false,
)

/**
 * Automatic captions' work outside the editor (Phase 21A): the timeline's sound without music is
 * exported at a speech bitrate and cut at quiet moments into parts the cloud accepts, then the
 * parts go up one after another. Words are moved to timeline time.
 */
class CaptionJob @Inject constructor(
    private val exporter: VideoExporter,
    private val speech: SpeechAudio,
    private val transcriber: Transcriber,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Exports and cuts [project]'s sound; parts longer than [maxPartMs] are split. With
     * [maxSeconds], only the parts whose charge fits are kept. [onProgress] gets 0–100.
     */
    suspend fun prepare(project: Project, maxPartMs: Long, maxSeconds: Int? = null, onProgress: (Int) -> Unit = {}): SpeechPrep {
        val durationMs = project.totalDurationMs()
        val file = exportSound(project, onProgress) ?: return SpeechPrep.Failed
        val made = mutableListOf<Pair<SpeechPart, File>>()
        try {
            val all = if (durationMs <= maxPartMs) {
                listOf(SpeechPart(0L, durationMs))
            } else {
                val levels = try {
                    speech.levels(file, LEVEL_WINDOW_MS)
                } catch (_: IOException) {
                    // Without levels the cuts fall at the limit; a word may be cut, nothing worse.
                    FloatArray(0)
                }
                SpeechParts.plan(durationMs, maxPartMs, levels, LEVEL_WINDOW_MS)
            }
            val parts = maxSeconds?.let { SpeechParts.affordable(all, it) } ?: all
            if (parts.isEmpty()) {
                withContext(io) { file.delete() }
                return SpeechPrep.NoMinutes
            }
            if (parts.size == 1 && parts[0].endMs == durationMs) {
                made += parts[0] to file
                return SpeechPrep.Ready(PreparedSpeech(made.toList(), durationMs))
            }
            parts.forEachIndexed { index, part ->
                val target = File(file.parentFile, "${file.nameWithoutExtension}-${index + 1}.m4a")
                speech.cut(file, part, target)
                made += part to target
                onProgress(EXPORT_SHARE + (100 - EXPORT_SHARE) * (index + 1) / parts.size)
            }
            withContext(io) { file.delete() }
            return SpeechPrep.Ready(PreparedSpeech(made.toList(), durationMs))
        } catch (_: IOException) {
            withContext(NonCancellable + io) { (made.map { it.second } + file).distinct().forEach(File::delete) }
            return SpeechPrep.Failed
        } catch (e: CancellationException) {
            withContext(NonCancellable + io) { (made.map { it.second } + file).distinct().forEach(File::delete) }
            throw e
        }
    }

    /** Just [range] of [project]'s sound (regenerating one line, Phase 21B), as one part. */
    suspend fun prepareRange(project: Project, range: SpeechPart, onProgress: (Int) -> Unit = {}): SpeechPrep {
        val file = exportSound(project, onProgress) ?: return SpeechPrep.Failed
        val target = File(file.parentFile, "${file.nameWithoutExtension}-line.m4a")
        try {
            speech.cut(file, range, target)
            return SpeechPrep.Ready(PreparedSpeech(listOf(range to target), range.durationMs))
        } catch (_: IOException) {
            withContext(NonCancellable + io) { target.delete() }
            return SpeechPrep.Failed
        } catch (e: CancellationException) {
            withContext(NonCancellable + io) { target.delete() }
            throw e
        } finally {
            withContext(NonCancellable + io) { file.delete() }
        }
    }

    /** The timeline's sound without music, as an M4A; null when it couldn't be exported. */
    private suspend fun exportSound(project: Project, onProgress: (Int) -> Unit): File? {
        val state = exporter.exportSpeech(project.withoutMusic())
            .onEach { if (it is ExportState.Running) it.percent?.let { p -> onProgress(p * EXPORT_SHARE / 100) } }
            .first { it !is ExportState.Running }
        return (state as? ExportState.Success)?.file
    }

    /**
     * Sends [speech]'s parts in order with [consent] and [language] (null lets the provider tell),
     * then deletes them. Between parts, [shouldStop] can end the job; a failure ends it too. Words
     * from the parts that finished are kept either way. [onPart] gets each part's index as it starts.
     */
    suspend fun send(
        speech: PreparedSpeech,
        language: String?,
        consent: UploadConsent,
        shouldStop: () -> Boolean = { false },
        onPart: (Int) -> Unit = {},
    ): HeardSpeech {
        val words = mutableListOf<TranscriptWord>()
        var heardLanguage: String? = null
        var covered = 0L
        try {
            speech.parts.forEachIndexed { index, (part, file) ->
                if (index > 0 && shouldStop()) return HeardSpeech(words, heardLanguage, covered, stopped = true)
                onPart(index)
                when (val result = transcriber.transcribe(file, MIME_TYPE, part.durationMs, language, consent)) {
                    is AiResult.Ok -> {
                        words += result.value.words.map { it.copy(startMs = it.startMs + part.startMs, endMs = it.endMs + part.startMs) }
                        heardLanguage = heardLanguage ?: result.value.language
                        covered = part.endMs
                    }
                    is AiResult.Failed -> return HeardSpeech(words, heardLanguage, covered, failure = result.failure)
                }
            }
            return HeardSpeech(words, heardLanguage, covered)
        } finally {
            withContext(NonCancellable + io) { speech.discard() }
        }
    }

    /** Deletes [speech]'s files without sending them (the user said no). */
    suspend fun discard(speech: PreparedSpeech) = withContext(NonCancellable + io) { speech.discard() }

    companion object {
        const val MIME_TYPE = "audio/mp4"
        const val LEVEL_WINDOW_MS = 50L

        /** The export's share of preparing, in percent; cutting is the rest. */
        private const val EXPORT_SHARE = 80
    }
}
