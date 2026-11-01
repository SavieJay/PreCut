package com.savie.precut.ui.editor

import com.savie.precut.ai.cloud.CloudClient
import com.savie.precut.data.AppSettings
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.ai.edit.AiChangeLabel
import com.savie.precut.model.ai.edit.EditEnd
import com.savie.precut.model.ai.edit.EditProgress
import com.savie.precut.model.ai.edit.EditSession
import com.savie.precut.model.ai.edit.MediaFacts
import com.savie.precut.model.ai.edit.replayChanges
import com.savie.precut.media.ai.MediaBriefMaker
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.pro.ProAccess
import com.savie.precut.model.pro.ProFeature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Prompt-to-edit's screens, one at a time. */
sealed interface AiEditUi {
    data object Closed : AiEditUi

    /**
     * The request sheet. [followUp] refines the changes under review; [reply] is what Claude said
     * when it made no change; [runsLeft] is null until the server has said.
     */
    data class Prompt(
        val request: String = "",
        val followUp: Boolean = false,
        val runsLeft: Int? = null,
        val consented: Boolean = false,
        val reply: String? = null,
        val failure: AiFailure? = null,
    ) : AiEditUi

    /** Claude at work; [understanding] is the media brief's progress (0–100) before the first step. */
    data class Working(val progress: EditProgress, val understanding: Int? = null) : AiEditUi

    /** The changes, each kept or not ([kept] indexes [changes]); [note] says why a run ended early. */
    data class Review(
        val changes: List<AiChangeLabel>,
        /** Each change's step, for grouping. */
        val steps: List<Int>,
        val kept: Set<Int>,
        val message: String,
        val note: ReviewNote?,
    ) : AiEditUi
}

sealed interface ReviewNote {
    data object OutOfSteps : ReviewNote
    data object CutShort : ReviewNote
    data object Stopped : ReviewNote
    data class Failed(val failure: AiFailure) : ReviewNote
}

/**
 * Prompt-to-edit (Phase 22C): the request, Claude's steps (run on a copy through the editor's own
 * rules), the review with the preview playing the changes, and applying the kept ones as one undo
 * step. The real project isn't touched before Apply.
 */
class EditorAiEdit(
    private val host: EditorHost,
    private val cloud: CloudClient,
    private val settings: AppSettings,
    private val pro: EditorPro,
    private val briefMaker: MediaBriefMaker,
    /** Opens the captions sheet in a style (for captions Claude asked for, after applying). */
    private val openCaptions: (styleId: String) -> Unit,
) {
    /** The media brief, made once per project opened in the sheet. */
    private var facts: MediaFacts? = null
    private var factsFor: String? = null
    private val _state = MutableStateFlow<AiEditUi>(AiEditUi.Closed)
    val state: StateFlow<AiEditUi> = _state.asStateFlow()

    private var session: EditSession? = null
    private var work: Job? = null

    fun onOpen() {
        val project = host.ready?.project ?: return
        if (project.clips.isEmpty() || _state.value != AiEditUi.Closed) return
        session = null
        _state.value = AiEditUi.Prompt(consented = settings.values.value.aiEditConsented)
        host.scope.launch {
            val left = (cloud.status() as? AiResult.Ok)?.value?.editRunsLeft ?: return@launch
            _state.update { (it as? AiEditUi.Prompt)?.copy(runsLeft = left) ?: it }
        }
    }

    fun onRequestChange(request: String) = _state.update { (it as? AiEditUi.Prompt)?.copy(request = request) ?: it }

    fun onSend() {
        val prompt = _state.value as? AiEditUi.Prompt ?: return
        val request = prompt.request.trim().takeIf { it.isNotEmpty() } ?: return
        val ready = host.ready ?: return
        if (!prompt.consented) settings.update { it.copy(aiEditConsented = true) }
        val current = session ?: EditSession(ready.project, host::newId, cloud::editStep).also { session = it }
        val selected = setOfNotNull(ready.selectedClipId, ready.selectedTextId, ready.selectedAudioId)
        _state.value = AiEditUi.Working(EditProgress(1, current.changes.size), understanding = 0)
        val keptBefore = (0 until current.changes.size).toSet()
        work = host.scope.launch {
            val end = try {
                // What the phone can read from the media, once: loudness, shots and what's in them.
                if (factsFor != current.original.id) {
                    facts = briefMaker.facts(current.original) { f ->
                        _state.update { if (it is AiEditUi.Working) it.copy(understanding = (f * 100).toInt()) else it }
                    }
                    factsFor = current.original.id
                }
                current.ask(request, UUID.randomUUID().toString(), host.positionMs, selected, facts) { progress -> _state.value = AiEditUi.Working(progress) }
            } catch (e: CancellationException) {
                // Stop keeps what's made; closing (session gone) keeps nothing.
                if (session === current) show(current, keptBefore, EditEnd.Stopped(refused = false, message = ""), request, stopped = true)
                throw e
            }
            show(current, keptBefore, end, request, stopped = false)
        }
    }

    /** Stops after the step being asked; the changes so far stay for review. */
    fun onStop() {
        work?.cancel()
    }

    fun onToggle(index: Int) {
        val review = _state.value as? AiEditUi.Review ?: return
        val kept = if (index in review.kept) review.kept - index else review.kept + index
        _state.value = review.copy(kept = kept)
        previewKept(kept)
    }

    fun onApply() {
        val review = _state.value as? AiEditUi.Review ?: return
        val current = session ?: return
        val project = host.ready?.project ?: return
        // Run on the project as it is now, in case it changed (undo) during the review.
        val replay = replayChanges(project, current.changes, review.kept, current.aliases)
        close()
        if (replay.edit.actions.any { !(it is EditAction.Batch && it.actions.isEmpty()) }) {
            host.applyEdit(replay.edit)
            host.send(EditorEvent.AiEditApplied(review.kept.size))
        }
        // Captions Claude asked for are made now, with the usual consent for the sound.
        review.kept.mapNotNull { current.changes.getOrNull(it)?.label as? AiChangeLabel.Captions }.firstOrNull()?.let { openCaptions(it.styleId) }
    }

    /** Apply all: every change, kept or switched off. */
    fun onApplyAll() {
        val review = _state.value as? AiEditUi.Review ?: return
        _state.value = review.copy(kept = review.changes.indices.toSet())
        onApply()
    }

    /** A follow-up request on the kept changes. */
    fun onRefine() {
        val review = _state.value as? AiEditUi.Review ?: return
        session?.keepOnly(review.kept)
        _state.value = AiEditUi.Prompt(followUp = true, consented = true)
    }

    /** Closes the request sheet: back to the review when there are changes, else out. */
    fun onDismissPrompt() {
        val current = session
        if (current == null || current.changes.isEmpty()) {
            close()
        } else {
            val all = (0 until current.changes.size).toSet()
            _state.value = review(current, all, message = "", note = null)
            previewKept(all)
        }
    }

    fun onDiscard() = close()

    private fun show(current: EditSession, keptBefore: Set<Int>, end: EditEnd, request: String, stopped: Boolean) {
        val newOnes = (keptBefore.size until current.changes.size).toSet()
        if (newOnes.isEmpty()) {
            if (current.changes.isNotEmpty() && stopped) {
                _state.value = review(current, keptBefore, message = "", note = null)
                previewKept(keptBefore)
                return
            }
            val failure = (end as? EditEnd.Failed)?.failure ?: if (end == EditEnd.OutOfSteps) AiFailure.StepsUsed else null
            // A free user out of AI requests: the paywall, from their own tap on Send.
            if (failure is AiFailure.EditRunsUsed && pro.access(ProFeature.AI_EDIT) != ProAccess.Unlocked) host.send(EditorEvent.OpenPaywall(ProFeature.AI_EDIT))
            val reply = when (end) {
                is EditEnd.Done -> end.message
                is EditEnd.Stopped -> end.message
                else -> null
            }
            _state.value = AiEditUi.Prompt(request, current.changes.isNotEmpty(), current.editRunsLeft, consented = true, reply = reply?.ifBlank { null }, failure = failure)
            return
        }
        val kept = keptBefore + newOnes
        val note = when {
            stopped -> ReviewNote.Stopped
            end == EditEnd.OutOfSteps -> ReviewNote.OutOfSteps
            end is EditEnd.Stopped -> ReviewNote.CutShort
            end is EditEnd.Failed -> ReviewNote.Failed(end.failure)
            else -> null
        }
        val message = (end as? EditEnd.Done)?.message ?: (end as? EditEnd.Stopped)?.message.orEmpty()
        _state.value = review(current, kept, message, note)
        previewKept(kept)
    }

    private fun review(current: EditSession, kept: Set<Int>, message: String, note: ReviewNote?) =
        AiEditUi.Review(current.changes.map { it.label }, current.changes.map { it.step }, kept, message, note)

    /** The preview plays the original with the [kept] changes. */
    private fun previewKept(kept: Set<Int>) {
        val current = session ?: return
        host.showInstead(replayChanges(current.original, current.changes, kept, current.aliases).project)
    }

    private fun close() {
        work?.cancel()
        work = null
        session = null
        _state.value = AiEditUi.Closed
        host.showInstead(null)
    }

    /** The editor is leaving: nothing to keep. */
    fun onCleared() {
        work?.cancel()
    }
}
