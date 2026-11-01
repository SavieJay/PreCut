package com.savie.precut.model.ai.edit

import com.savie.precut.model.Project
import com.savie.precut.model.edit.EditAction
import kotlinx.serialization.json.JsonObject

/** One tool call from Claude: [id] to answer, the tool's [name] and its JSON [input]. */
data class EditToolCall(val id: String, val name: String, val input: JsonObject)

/** What running a tool call did. */
sealed interface ToolOutcome {
    /** [action] took the project to [project], making [newIds]; [result] goes back to Claude. */
    data class Applied(
        val action: EditAction,
        val project: Project,
        val newIds: List<String>,
        val label: AiChangeLabel,
        val result: String,
    ) : ToolOutcome

    /** Nothing changed; [reason] tells Claude why, so it can try again. */
    data class Rejected(val reason: String) : ToolOutcome
}

/** A change Claude made, kept for review: run again (with the same [newIds]) to apply it. */
data class AiChange(val call: EditToolCall, val newIds: List<String>, val label: AiChangeLabel)

/**
 * What the review list says about a change, in the app's words (the UI turns each into a string).
 * Clips are numbered from 1 in timeline order, as the change found them; times are timeline ms.
 */
sealed interface AiChangeLabel {
    data class Trim(val clip: Int, val lengthMs: Long) : AiChangeLabel
    data class Split(val clip: Int, val atMs: Long) : AiChangeLabel
    data class DeleteClips(val count: Int) : AiChangeLabel
    data class Move(val clip: Int, val toPosition: Int) : AiChangeLabel
    data class Duplicate(val clip: Int) : AiChangeLabel
    data class Speed(val clip: Int, val speed: Float) : AiChangeLabel
    data class Sound(val clip: Int) : AiChangeLabel
    data class Look(val clip: Int) : AiChangeLabel
    data class Transition(val clip: Int, val removed: Boolean) : AiChangeLabel
    data class Animation(val clip: Int) : AiChangeLabel
    data class AddText(val text: String) : AiChangeLabel
    data class EditText(val text: String) : AiChangeLabel
    data class DeleteTexts(val count: Int) : AiChangeLabel
    data class AddEffect(val startMs: Long, val endMs: Long) : AiChangeLabel
    data class Ratio(val ratio: String) : AiChangeLabel
    data class Music(val name: String) : AiChangeLabel
    data class CutToBeats(val clips: Int) : AiChangeLabel
}

/** The project with the [kept] changes (indexes into [changes]) applied, as one undo step. */
data class AiReplay(val project: Project, val edit: EditAction.Batch, val skipped: Int)

/**
 * Runs the [kept] changes again on [original], in order, each with the ids it made the first
 * time, so keeping every change gives exactly what was previewed. A change that no longer applies
 * (it needed one that was left out) is skipped and counted.
 */
fun replayChanges(original: Project, changes: List<AiChange>, kept: Set<Int>, aliases: IdAliases): AiReplay {
    var project = original
    val actions = mutableListOf<EditAction>()
    var skipped = 0
    changes.forEachIndexed { index, change ->
        if (index !in kept) return@forEachIndexed
        val ids = change.newIds.iterator()
        val outcome = EditTools.run(project, change.call, aliases) { if (ids.hasNext()) ids.next() else fail("This change needs an id it didn't make before") }
        if (outcome is ToolOutcome.Applied) {
            project = outcome.project
            actions += outcome.action
        } else {
            skipped++
        }
    }
    return AiReplay(project, EditAction.Batch(actions), skipped)
}
