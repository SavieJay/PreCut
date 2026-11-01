package com.savie.precut.model.ai.edit

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.Project
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.totalDurationMs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditToolsTest {

    /** A valid call of every tool on [editProject]. */
    private val samples = mapOf(
        "trim_clip" to """{"clip_id":"c1","keep_from_seconds":1,"keep_to_seconds":4}""",
        "split_clip" to """{"clip_id":"c2","at_seconds":5}""",
        "delete_clips" to """{"clip_ids":["c3"]}""",
        "move_clip" to """{"clip_id":"c1","to_position":2}""",
        "duplicate_clip" to """{"clip_id":"c1"}""",
        "set_clip_speed" to """{"clip_id":"c1","speed":2}""",
        "set_clip_sound" to """{"clip_id":"c1","volume_percent":50,"muted":null,"fade_in_seconds":1,"fade_out_seconds":null}""",
        "set_clip_look" to """{"clip_id":"c1","filter_id":"basic.vivid","filter_strength_percent":80,"brightness":10,"contrast":null,"saturation":null,"exposure":null,"temperature":20,"vignette":null}""",
        "set_transition" to """{"after_clip_id":"c1","transition":"dissolve","duration_seconds":0.8}""",
        "set_clip_animation" to """{"clip_id":"c1","in_animation":"fade","out_animation":null,"combo":null,"in_seconds":1,"out_seconds":null}""",
        "add_text" to """{"text":"Summer","start_seconds":1,"end_seconds":4,"position":"top","template_id":"headline"}""",
        "edit_text" to """{"text_id":"t1","text":"Hi there","start_seconds":null,"end_seconds":3}""",
        "delete_texts" to """{"text_ids":["t1"]}""",
        "add_effect" to """{"effect_id":"snow","start_seconds":0,"end_seconds":5,"intensity_percent":70}""",
        "set_aspect_ratio" to """{"ratio":"1:1"}""",
        "set_audio_track" to """{"audio_id":"a1","volume_percent":60,"fade_in_seconds":null,"fade_out_seconds":2,"start_seconds":1}""",
        "cut_to_beats" to """{"audio_id":"a1"}""",
    )

    private fun run(name: String, input: String, project: Project = editProject()): ToolOutcome =
        EditTools.run(project, call(name, input), editAliases(project), counter())

    private fun applied(name: String, input: String) = run(name, input) as? ToolOutcome.Applied
        ?: throw AssertionError("$name was rejected: ${(run(name, input) as ToolOutcome.Rejected).reason}")

    private fun reason(name: String, input: String) = (run(name, input) as? ToolOutcome.Rejected)?.reason
        ?: throw AssertionError("$name should have been rejected")

    private fun assertReason(name: String, input: String, expected: String) {
        val reason = reason(name, input)
        assertTrue("$name: \"$reason\" should mention \"$expected\"", reason.contains(expected))
    }

    @Test
    fun `every tool the server offers is run on the phone, and every sample fits its schema`() {
        val tools = Json.parseToJsonElement(File("../supabase/functions/_shared/edit_tools.json").readText()).jsonArray.map { it.jsonObject }
        assertEquals(tools.map { it.getValue("name").jsonPrimitive.content }, EditTools.names)
        assertEquals(EditTools.names.toSet(), samples.keys)
        tools.forEach { tool ->
            val name = tool.getValue("name").jsonPrimitive.content
            val schema = tool.getValue("input_schema").jsonObject
            val required = schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet()
            val properties = schema.getValue("properties").jsonObject.keys
            assertEquals("$name: strict tools require every property", properties, required)
            assertEquals("$name's sample", required, json(samples.getValue(name)).keys)
        }
    }

    @Test
    fun `every tool's sample call applies through the reducer`() {
        samples.forEach { (name, input) -> assertNotNull(name, applied(name, input)) }
    }

    @Test
    fun `clip tools change the timeline and report it`() {
        val trim = applied("trim_clip", samples.getValue("trim_clip"))
        assertEquals(1_000L, trim.project.clips[0].trimStartMs)
        assertEquals(4_000L, trim.project.clips[0].trimEndMs)
        assertEquals(AiChangeLabel.Trim(1, 3_000L), trim.label)
        val timeline = json(trim.result).getValue("timeline").jsonObject
        assertEquals(26.0, timeline.getValue("duration_seconds").jsonPrimitive.content.toDouble(), 0.0)

        val split = applied("split_clip", samples.getValue("split_clip"))
        assertEquals(listOf("v1", "v2", "n1", "v3", "img"), split.project.clips.map { it.id })
        assertEquals("c5", json(split.result).getValue("new_id").jsonPrimitive.content)
        assertEquals(listOf("n1"), split.newIds)

        assertEquals(listOf("v2", "v3", "v1", "img"), applied("move_clip", samples.getValue("move_clip")).project.clips.map { it.id })
        assertEquals(5_000L, applied("set_clip_speed", samples.getValue("set_clip_speed")).project.clips[0].timelineDurationMs())
    }

    @Test
    fun `looks, motion, texts, effects and audio land where the tools say`() {
        val look = applied("set_clip_look", samples.getValue("set_clip_look")).project.clips[0].look
        assertEquals("basic.vivid", look.filterId)
        assertEquals(0.8f, look.filterIntensity, 0.001f)
        assertEquals(20f, look.adjust.temperature, 0f)
        val transition = applied("set_transition", samples.getValue("set_transition")).project.clips[0].transitionOut
        assertEquals(TransitionType.DISSOLVE, transition?.type)
        assertEquals(800L, transition?.durationMs)
        val text = applied("add_text", samples.getValue("add_text")).project.textOverlays.last()
        assertEquals("Summer", text.text)
        assertEquals(0.15f, text.yNorm, 0f)
        assertEquals(AspectRatio.RATIO_1_1, applied("set_aspect_ratio", samples.getValue("set_aspect_ratio")).project.aspectRatio)
        val music = applied("set_audio_track", samples.getValue("set_audio_track")).project.audioClips[0]
        assertEquals(0.6f, music.volume, 0.001f)
        assertEquals(1_000L, music.timelineStartMs)
        assertEquals(2_000L, music.fadeOutMs)
        val beats = applied("cut_to_beats", samples.getValue("cut_to_beats"))
        assertTrue(beats.label is AiChangeLabel.CutToBeats)
        assertTrue(beats.project.totalDurationMs() < editProject().totalDurationMs())
    }

    @Test
    fun `what can't be done goes back to Claude as a reason`() {
        assertReason("trim_clip", """{"clip_id":"c9","keep_from_seconds":0,"keep_to_seconds":1}""", "no clip c9")
        assertReason("trim_clip", """{"clip_id":"c1","keep_from_seconds":0,"keep_to_seconds":30}""", "only 10.0 seconds")
        assertReason("delete_clips", """{"clip_ids":["c1","c2","c3","c4"]}""", "At least one clip")
        assertReason("set_transition", """{"after_clip_id":"c4","transition":"fade_black","duration_seconds":null}""", "last clip")
        assertReason("set_clip_look", """{"clip_id":"c1","filter_id":"sepia","filter_strength_percent":null,"brightness":null,"contrast":null,"saturation":null,"exposure":null,"temperature":null,"vignette":null}""", "catalog.filters")
        assertReason("set_clip_speed", """{"clip_id":"c4","speed":2}""", "Photos")
        assertReason("set_clip_speed", """{"clip_id":"c1","speed":500}""", "from 0.1 to 100")
        assertReason("move_clip", """{"clip_id":"c1","to_position":0}""", "already")
        assertReason("set_aspect_ratio", """{"ratio":"9:16"}""", "changes nothing")
        assertReason("delete_texts", """{"text_ids":["c1"]}""", "isn't a text")
        assertReason("unknown_tool", "{}", "no tool")
        assertReason("add_text", """{"text":"x","start_seconds":"soon","end_seconds":4,"position":"top","template_id":null}""", "must be a number")
    }

    @Test
    fun `a photo's trim sets how long it shows`() {
        val photo = applied("trim_clip", """{"clip_id":"c4","keep_from_seconds":2,"keep_to_seconds":7}""").project.clips[3]
        assertEquals(0L, photo.trimStartMs)
        assertEquals(5_000L, photo.trimEndMs)
    }

    @Test
    fun `a text's words and times change, null keeping the rest`() {
        val outcome = applied("edit_text", samples.getValue("edit_text"))
        assertEquals("Hi there", outcome.project.textOverlays[0].text)
        assertEquals(0L, outcome.project.textOverlays[0].startMs)
        assertEquals(3_000L, outcome.project.textOverlays[0].endMs)
    }
}
