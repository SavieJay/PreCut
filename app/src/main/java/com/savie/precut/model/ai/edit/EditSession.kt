package com.savie.precut.model.ai.edit

import com.savie.precut.model.Project
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** One step's answer from the server: Claude's reply to append, and what's left. */
data class EditStepReply(val content: JsonArray, val stopReason: String, val stepsLeft: Int, val editRunsLeft: Int)

/** How far a request has got: the step being asked, and the changes made so far. */
data class EditProgress(val step: Int, val changes: Int)

/** How a request ended. Whatever ended it, the changes made so far stay for review. */
sealed interface EditEnd {
    /** Claude finished; [message] is what it said it did. */
    data class Done(val message: String) : EditEnd

    /** The run's steps ran out before Claude finished. */
    data object OutOfSteps : EditEnd

    /** Claude declined ([refused]) or its reply was cut short. */
    data class Stopped(val refused: Boolean, val message: String) : EditEnd

    data class Failed(val failure: AiFailure) : EditEnd
}

/**
 * Prompt-to-edit (Phase 22C) on [original]: asks Claude step by step through [step] (the server
 * call), runs its tool calls on a working copy with [EditTools], and keeps every change for
 * review. Nothing touches the real project until the person applies a selection.
 */
class EditSession(
    val original: Project,
    private val newId: () -> String,
    private val step: suspend (runId: String, messages: JsonArray) -> AiResult<EditStepReply>,
    private val maxSteps: Int = MAX_STEPS,
) {
    val aliases = IdAliases()
    private val messages = mutableListOf<JsonObject>()
    private val made = mutableListOf<AiChange>()

    /** Every change so far, in order. */
    val changes: List<AiChange> get() = made

    /** [original] with every change. */
    var proposal: Project = original
        private set

    /** Requests left this month, as the server last said; null before the first step. */
    var editRunsLeft: Int? = null
        private set

    /**
     * Asks for [request] as a new run [runId] (a fresh UUID). The brief describes [proposal] with
     * the playhead and selection; a follow-up says it's the project "now".
     */
    suspend fun ask(request: String, runId: String, playheadMs: Long, selectedIds: Set<String>, onProgress: (EditProgress) -> Unit): EditEnd {
        val brief = proposal.editBrief(aliases, playheadMs, selectedIds)
        val blocks = EditMessages.request(brief, request, followUp = messages.isNotEmpty())
        // After a run that stopped with tool results, the request joins that user message.
        val pending = messages.lastOrNull()?.let(EditMessages::userBlocks)
        val before = messages.toList()
        if (pending != null) messages[messages.lastIndex] = EditMessages.user(pending + blocks) else messages += EditMessages.user(blocks)
        for (stepNumber in 1..maxSteps) {
            onProgress(EditProgress(stepNumber, made.size))
            val reply = when (val result = step(runId, JsonArray(messages.toList()))) {
                is AiResult.Ok -> result.value
                is AiResult.Failed -> {
                    if (stepNumber == 1) messages.restore(before)
                    return if (result.failure == AiFailure.StepsUsed) EditEnd.OutOfSteps else EditEnd.Failed(result.failure)
                }
            }
            editRunsLeft = reply.editRunsLeft
            messages += EditMessages.assistant(reply.content)
            val calls = EditMessages.toolCalls(reply.content)
            val text = EditMessages.text(reply.content)
            if (calls.isEmpty()) return endOf(reply.stopReason, text)
            if (reply.stopReason != STOP_TOOL_USE) {
                // A reply cut short may end in half a tool call: answer every call without running it.
                messages += EditMessages.user(calls.map { EditMessages.toolResult(it.id, CUT_SHORT, isError = true) })
                return endOf(reply.stopReason, text)
            }
            messages += EditMessages.user(calls.map(::run))
            if (reply.stepsLeft <= 0) return EditEnd.OutOfSteps
        }
        return EditEnd.OutOfSteps
    }

    /** Drops the changes not in [kept] (indexes into [changes]) before a follow-up, so Claude sees what's kept. */
    fun keepOnly(kept: Set<Int>) {
        if (kept.size == made.size) return
        val replay = replayChanges(original, made, kept, aliases)
        val keptChanges = made.filterIndexed { index, _ -> index in kept }
        made.clear()
        made += keptChanges
        proposal = replay.project
    }

    private fun run(call: EditToolCall): JsonObject = when (val outcome = EditTools.run(proposal, call, aliases, newId)) {
        is ToolOutcome.Applied -> {
            proposal = outcome.project
            made += AiChange(call, outcome.newIds, outcome.label)
            EditMessages.toolResult(call.id, outcome.result, isError = false)
        }
        is ToolOutcome.Rejected -> EditMessages.toolResult(call.id, outcome.reason, isError = true)
    }

    private fun endOf(stopReason: String, text: String): EditEnd = when (stopReason) {
        STOP_REFUSAL -> EditEnd.Stopped(refused = true, message = text)
        STOP_MAX_TOKENS -> EditEnd.Stopped(refused = false, message = text)
        else -> EditEnd.Done(text)
    }

    private fun MutableList<JsonObject>.restore(before: List<JsonObject>) {
        clear()
        addAll(before)
    }

    companion object {
        /** The app's own cap on steps per request (the server has its own, usually the same). */
        const val MAX_STEPS = 10
        private const val STOP_TOOL_USE = "tool_use"
        private const val STOP_REFUSAL = "refusal"
        private const val STOP_MAX_TOKENS = "max_tokens"
        private const val CUT_SHORT = "Your reply was cut short, so this call wasn't run."
    }
}
