package com.savie.precut.ui.editor

import com.savie.precut.ai.CaptionJob
import com.savie.precut.ai.HeardSpeech
import com.savie.precut.ai.PreparedSpeech
import com.savie.precut.ai.SpeechPrep
import com.savie.precut.ai.UploadConsent
import com.savie.precut.ai.cloud.CloudClient
import com.savie.precut.data.AppSettings
import com.savie.precut.media.PreviewController
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.ai.CloudStatus
import com.savie.precut.model.ai.SpeechPart
import com.savie.precut.model.ai.SpeechParts
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.text.CaptionLines
import com.savie.precut.model.text.CaptionStyle
import com.savie.precut.model.text.captionCount
import com.savie.precut.model.text.hasSoundBesidesMusic
import com.savie.precut.model.text.wordsHeardIn
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Captions (Phase 21A): the sheet's choices and free minutes, then preparing the sound, the
 * consent dialog, sending part by part and adding the captions in one undo step. Stop ends the job
 * after the part being sent; the captions made so far are kept.
 */
class EditorCaptions(
    private val host: EditorHost,
    private val preview: PreviewController,
    private val job: CaptionJob,
    private val cloud: CloudClient,
    private val settings: AppSettings,
) {
    private val _state = MutableStateFlow<CaptionsUi>(CaptionsUi.Closed)
    val state: StateFlow<CaptionsUi> = _state.asStateFlow()

    private var quotaJob: Job? = null
    private var work: Job? = null

    /** Prepared sound waiting for consent, and what it's for. */
    private var prepared: PreparedSpeech? = null
    private var pending: Pending? = null
    private var stopRequested = false

    /** What the job in progress makes: captions for the whole timeline, or one line again. */
    private sealed interface Pending {
        data class Whole(val options: CaptionsUi.Options) : Pending
        data class Line(val textId: String, val language: String?) : Pending
    }

    fun onOpen() {
        val project = host.ready?.project ?: return
        if (project.clips.isEmpty() || _state.value != CaptionsUi.Closed) return
        val values = settings.values.value
        _state.value = CaptionsUi.Options(
            language = values.captionLanguage?.takeIf { it in CAPTION_LANGUAGES },
            style = CaptionStyle.byId(values.captionStyleId) ?: CaptionStyle.DEFAULT,
            durationMs = project.totalDurationMs(),
            neededSeconds = 0,
            firstPartSeconds = 0,
            reachMs = 0L,
            quota = CaptionQuota.Checking,
            replacing = project.captionCount(),
            hasSound = project.hasSoundBesidesMusic(),
        ).withCosts(status = null)
        quotaJob?.cancel()
        quotaJob = host.scope.launch {
            val quota = when (val result = cloud.status()) {
                is AiResult.Ok -> result.value?.let(CaptionQuota::Known) ?: CaptionQuota.NotUsed
                is AiResult.Failed -> CaptionQuota.Unavailable(result.failure)
            }
            _state.update { (it as? CaptionsUi.Options)?.copy(quota = quota)?.withCosts((quota as? CaptionQuota.Known)?.status) ?: it }
        }
    }

    fun onLanguage(language: String?) {
        _state.update { (it as? CaptionsUi.Options)?.copy(language = language) ?: it }
        settings.update { it.copy(captionLanguage = language) }
    }

    fun onStyle(style: CaptionStyle) {
        _state.update { (it as? CaptionsUi.Options)?.copy(style = style) ?: it }
        settings.update { it.copy(captionStyleId = style.id) }
    }

    /** Closes the sheet or a result; a running job only stops through [onStop]. */
    fun onDismiss() {
        when (_state.value) {
            is CaptionsUi.Options, is CaptionsUi.Failed, is CaptionsUi.Partial -> {
                quotaJob?.cancel()
                _state.value = CaptionsUi.Closed
            }
            else -> Unit
        }
    }

    /** Exports and cuts the sound, then asks for consent. */
    fun onGenerate() {
        val options = _state.value as? CaptionsUi.Options ?: return
        if (!options.canGenerate) return
        val project = host.ready?.project ?: return
        val status = (options.quota as? CaptionQuota.Known)?.status
        quotaJob?.cancel()
        preview.pause()
        pending = Pending.Whole(options)
        _state.value = CaptionsUi.Preparing(0)
        work = host.scope.launch {
            val prep = job.prepare(project, maxPartMs(status), status?.secondsLeft) { percent ->
                _state.update { if (it is CaptionsUi.Preparing) CaptionsUi.Preparing(percent) else it }
            }
            _state.value = when (prep) {
                is SpeechPrep.Ready -> {
                    prepared = prep.speech
                    val covered = prep.speech.parts.last().first.endMs
                    CaptionsUi.Consent(prep.speech.summary, coversAll = covered >= prep.speech.durationMs)
                }
                SpeechPrep.Failed -> CaptionsUi.Failed(CaptionFailure.SoundNotReadable)
                SpeechPrep.NoMinutes -> CaptionsUi.Failed(CaptionFailure.Cloud(AiFailure.QuotaUsed(status?.secondsLeft ?: 0, status?.resetsAtMs)))
            }
        }
    }

    /**
     * Regenerate one line (Phase 21B): only the line's few seconds (with a little either side) are
     * sent, and its words replaced by what's heard in its time; its times and style stay.
     */
    fun onRegenerateLine(textId: String) {
        if (_state.value != CaptionsUi.Closed) return
        val project = host.ready?.project ?: return
        val line = project.textOverlays.firstOrNull { it.id == textId && it.caption != null } ?: return
        val range = SpeechPart((line.startMs - LINE_PADDING_MS).coerceAtLeast(0L), (line.endMs + LINE_PADDING_MS).coerceAtMost(project.totalDurationMs()))
        preview.pause()
        pending = Pending.Line(textId, line.caption?.language)
        _state.value = CaptionsUi.Preparing(0)
        work = host.scope.launch {
            // Say why it can't be sent now, rather than after preparing the sound.
            lineBlocker(range)?.let {
                _state.value = CaptionsUi.Failed(it)
                return@launch
            }
            val prep = job.prepareRange(project, range) { percent ->
                _state.update { if (it is CaptionsUi.Preparing) CaptionsUi.Preparing(percent) else it }
            }
            _state.value = when (prep) {
                is SpeechPrep.Ready -> {
                    prepared = prep.speech
                    CaptionsUi.Consent(prep.speech.summary, coversAll = true)
                }
                SpeechPrep.Failed, SpeechPrep.NoMinutes -> CaptionsUi.Failed(CaptionFailure.SoundNotReadable)
            }
        }
    }

    private suspend fun lineBlocker(range: SpeechPart): CaptionFailure? = when (val result = cloud.status()) {
        is AiResult.Failed -> CaptionFailure.Cloud(result.failure)
        is AiResult.Ok -> result.value?.let { status ->
            when {
                !status.cloudEnabled -> CaptionFailure.Cloud(AiFailure.BudgetReached(status.resetsAtMs))
                status.secondsLeft < SpeechParts.chargedSeconds(listOf(range)) -> CaptionFailure.Cloud(AiFailure.QuotaUsed(status.secondsLeft, status.resetsAtMs))
                else -> null
            }
        }
    }

    fun onConsentSend() {
        val asked = _state.value as? CaptionsUi.Consent ?: return
        val speech = prepared ?: return
        val target = pending ?: return
        val language = when (target) {
            is Pending.Whole -> target.options.language
            is Pending.Line -> target.language
        }
        prepared = null
        // The only place captions' consent is given: the user just tapped Send for this summary.
        val consent = UploadConsent.givenFor(asked.summary)
        stopRequested = false
        _state.value = CaptionsUi.Sending(0, speech.parts.size)
        work = host.scope.launch {
            val heard = job.send(speech, language, consent, shouldStop = { stopRequested }) { part ->
                _state.update { (it as? CaptionsUi.Sending)?.copy(part = part) ?: it }
            }
            _state.value = when (target) {
                is Pending.Whole -> finish(heard, speech.durationMs, target.options)
                is Pending.Line -> finishLine(heard, target.textId)
            }
        }
    }

    fun onConsentCancel() {
        if (_state.value !is CaptionsUi.Consent) return
        val speech = prepared
        prepared = null
        _state.value = CaptionsUi.Closed
        speech?.let { host.scope.launch { job.discard(it) } }
    }

    /** Preparing stops at once; sending stops after the part being sent. */
    fun onStop() {
        when (_state.value) {
            is CaptionsUi.Preparing -> {
                work?.cancel()
                _state.value = CaptionsUi.Closed
            }
            is CaptionsUi.Sending -> {
                stopRequested = true
                _state.update { (it as? CaptionsUi.Sending)?.copy(stopping = true) ?: it }
            }
            else -> Unit
        }
    }

    /** The editor is closing: prepared sound that was never sent is deleted. */
    fun onCleared() {
        prepared?.discard()
        prepared = null
    }

    /** Adds what was heard as captions, in one undo step, and says how it went. */
    private fun finish(heard: HeardSpeech, durationMs: Long, options: CaptionsUi.Options): CaptionsUi {
        val lines = CaptionLines.build(heard.words)
        val texts = CaptionLines.texts(lines, groupId = host.newId(), style = options.style, language = heard.language, newId = host::newId)
        if (texts.isEmpty()) return CaptionsUi.Failed(heard.failure?.let(CaptionFailure::Cloud) ?: CaptionFailure.NoSpeech)
        host.applyEdit(EditAction.SetCaptions(texts))
        // Parts left out because the free minutes didn't reach them.
        val shortOfMinutes = heard.failure == null && !heard.stopped && heard.coveredMs < durationMs
        if (heard.failure == null && !heard.stopped && !shortOfMinutes) {
            host.send(EditorEvent.CaptionsAdded(texts.size))
            return CaptionsUi.Closed
        }
        val resetsAt = ((options.quota as? CaptionQuota.Known)?.status)?.resetsAtMs
        val failure = heard.failure ?: if (shortOfMinutes) AiFailure.QuotaUsed(0, resetsAt) else null
        return CaptionsUi.Partial(texts.size, heard.coveredMs, durationMs, failure)
    }

    /** What the timeline costs with [status]'s limits, and how far its free minutes reach. */
    /** Puts the words heard in the line's time into it. */
    private fun finishLine(heard: HeardSpeech, textId: String): CaptionsUi {
        heard.failure?.let { return CaptionsUi.Failed(CaptionFailure.Cloud(it)) }
        val line = host.ready?.project?.textOverlays?.firstOrNull { it.id == textId } ?: return CaptionsUi.Closed
        val words = line.wordsHeardIn(heard.words)
        if (words.isEmpty()) return CaptionsUi.Failed(CaptionFailure.NoSpeechInLine)
        host.applyEdit(EditAction.SetHeardWords(textId, words))
        host.send(EditorEvent.CaptionRegenerated)
        return CaptionsUi.Closed
    }

    private fun CaptionsUi.Options.withCosts(status: CloudStatus?): CaptionsUi.Options {
        val parts = SpeechParts.plan(durationMs, maxPartMs(status))
        val reach = status?.let { SpeechParts.affordable(parts, it.secondsLeft).lastOrNull()?.endMs ?: 0L } ?: durationMs
        return copy(
            neededSeconds = SpeechParts.chargedSeconds(parts),
            firstPartSeconds = SpeechParts.chargedSeconds(parts.take(1)),
            reachMs = reach,
        )
    }

    private companion object {
        /** The server's limit per upload until it says otherwise (docs/CLOUD_API.md). */
        const val DEFAULT_MAX_UPLOAD_SECONDS = 600

        /** Parts stay this far under the limit, so measured lengths that differ slightly still fit. */
        const val PART_MARGIN_MS = 2_000L

        /** Sound sent either side of a line being regenerated, so its first and last words are whole. */
        const val LINE_PADDING_MS = 300L

        fun maxPartMs(status: CloudStatus?): Long =
            ((status?.maxUploadSeconds ?: DEFAULT_MAX_UPLOAD_SECONDS) * 1_000L - PART_MARGIN_MS).coerceAtLeast(PART_MARGIN_MS)
    }
}
