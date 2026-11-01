package com.savie.precut.model.ai.edit

import com.savie.precut.model.Clip
import com.savie.precut.model.Project
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Prompt-to-edit's tools (supabase/functions/_shared/edit_tools.json), run on the phone: each call
 * becomes an [EditAction] applied by [EditReducer] to the working copy, so Claude's changes follow
 * exactly the rules the editor's own buttons do. Ids in and out are [IdAliases]; anything that
 * can't be done goes back to Claude as a reason instead of being guessed at.
 */
object EditTools {
    val names: List<String> = listOf(
        "trim_clip", "split_clip", "delete_clips", "move_clip", "duplicate_clip", "set_clip_speed",
        "set_clip_sound", "set_clip_look", "set_transition", "set_clip_animation", "add_text",
        "edit_text", "delete_texts", "add_effect", "set_aspect_ratio", "set_audio_track", "cut_to_beats",
    )

    /** Runs [call] on [project]; [newId] makes ids for anything new (passed in to keep this pure). */
    fun run(project: Project, call: EditToolCall, aliases: IdAliases, newId: () -> String): ToolOutcome = try {
        val tools = ToolContext(project, ToolInput(call.input), aliases, newId)
        val made = when (call.name) {
            "trim_clip" -> tools.trim()
            "split_clip" -> tools.split()
            "delete_clips" -> tools.deleteClips()
            "move_clip" -> tools.move()
            "duplicate_clip" -> tools.duplicate()
            "set_clip_speed" -> tools.speed()
            "set_clip_sound" -> tools.sound()
            "set_clip_look" -> tools.look()
            "set_transition" -> tools.transition()
            "set_clip_animation" -> tools.animation()
            "add_text" -> tools.addText()
            "edit_text" -> tools.editText()
            "delete_texts" -> tools.deleteTexts()
            "add_effect" -> tools.addEffect()
            "set_aspect_ratio" -> tools.aspectRatio()
            "set_audio_track" -> tools.audioTrack()
            "cut_to_beats" -> tools.cutToBeats()
            else -> fail("There's no tool called ${call.name}")
        }
        val edited = EditReducer.reduce(project, made.action)
        if (edited == project) {
            ToolOutcome.Rejected("That changes nothing: the project is already like that.")
        } else {
            ToolOutcome.Applied(made.action, edited, tools.madeIds, made.label, result(edited, made, aliases))
        }
    } catch (e: ToolError) {
        ToolOutcome.Rejected(e.message.orEmpty())
    }

    private fun result(edited: Project, made: Made, aliases: IdAliases): String = buildJsonObject {
        put("ok", true)
        made.newAlias?.let { put("new_id", it) }
        if (made.timelineChanged) put("timeline", edited.timelineBrief(aliases))
    }.toString()
}

/** A tool's change: the [action], its review [label], a [newAlias] to tell Claude, and whether clip timing moved. */
internal data class Made(
    val action: EditAction,
    val label: AiChangeLabel,
    val newAlias: String? = null,
    val timelineChanged: Boolean = false,
)

/** What every tool reads: the project, the call's input, the aliases, and ids for new items. */
internal class ToolContext(
    val project: Project,
    val input: ToolInput,
    val aliases: IdAliases,
    private val newId: () -> String,
) {
    val madeIds = mutableListOf<String>()

    fun nextId(): String = newId().also { madeIds += it }

    /** The main-track clip named by input [name], with its index. */
    fun clip(name: String = "clip_id"): IndexedValue<Clip> {
        val alias = input.string(name)
        val real = aliases.real(alias) ?: fail("There's no clip $alias")
        val index = project.clips.indexOfFirst { it.id == real }
        if (index < 0) fail("$alias isn't a clip on the main track")
        return IndexedValue(index, project.clips[index])
    }
}
