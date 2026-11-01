package com.savie.precut.model.ai.edit

import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSessionTest {

    private val thinking = json("""{"type":"thinking","thinking":"","signature":"sig=="}""")

    private fun toolUse(id: String, name: String, input: String) = json("""{"type":"tool_use","id":"$id","name":"$name","input":$input}""")

    private fun text(words: String) = json("""{"type":"text","text":"$words"}""")

    private fun reply(stop: String, vararg blocks: JsonObject, stepsLeft: Int = 5) = AiResult.Ok(EditStepReply(JsonArray(blocks.toList()), stop, stepsLeft, 9))

    /** A server that answers with [replies] in turn and records each conversation sent. */
    private class Script(val replies: List<AiResult<EditStepReply>>) {
        val sent = mutableListOf<JsonArray>()
        suspend fun step(@Suppress("UNUSED_PARAMETER") runId: String, messages: JsonArray): AiResult<EditStepReply> {
            sent += messages
            return replies[sent.size - 1]
        }
    }

    private fun session(script: Script, maxSteps: Int = EditSession.MAX_STEPS) = EditSession(editProject(), counter(), script::step, maxSteps)

    @Test
    fun `tool calls run on the copy, results go back, and Claude's reply is kept exactly`() = runTest {
        val first = arrayOf(thinking, toolUse("t1", "trim_clip", """{"clip_id":"c1","keep_from_seconds":0,"keep_to_seconds":5}"""), toolUse("t2", "set_clip_speed", """{"clip_id":"c4","speed":2}"""))
        val script = Script(listOf(reply("tool_use", *first), reply("end_turn", text("Trimmed the first clip."))))
        val session = session(script)
        val end = session.ask("Make it shorter", "run", 0L, emptySet()) {}
        assertEquals(EditEnd.Done("Trimmed the first clip."), end)
        assertEquals(1, session.changes.size)
        assertEquals(5_000L, session.proposal.clips[0].trimEndMs)
        // The original is untouched.
        assertEquals(10_000L, session.original.clips[0].trimEndMs)
        val second = script.sent[1]
        assertEquals(3, second.size)
        assertEquals(JsonArray(first.toList()), second[1].jsonObject.getValue("content"))
        val results = second[2].jsonObject.getValue("content").jsonArray.map { it.jsonObject }
        assertEquals(listOf("t1", "t2"), results.map { it.getValue("tool_use_id").jsonPrimitive.content })
        assertFalse(results[0].containsKey("is_error"))
        assertEquals("true", results[1].getValue("is_error").jsonPrimitive.content)
        assertEquals(9, session.editRunsLeft)
    }

    @Test
    fun `the first message holds the brief, then the request`() = runTest {
        val script = Script(listOf(reply("end_turn", text("Done"))))
        session(script).ask("Add a title", "run", 1_500L, setOf("v2")) {}
        val blocks = script.sent[0][0].jsonObject.getValue("content").jsonArray
        val brief = json(blocks[0].jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(1.5, brief.getValue("playhead_seconds").jsonPrimitive.content.toDouble(), 0.0)
        assertEquals("c2", brief.getValue("selected").jsonArray[0].jsonPrimitive.content)
        assertEquals("Add a title", blocks[1].jsonObject.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `out of steps keeps the changes, and a follow-up joins the pending results`() = runTest {
        val trim = toolUse("t1", "trim_clip", """{"clip_id":"c1","keep_from_seconds":0,"keep_to_seconds":5}""")
        val script = Script(listOf(reply("tool_use", trim, stepsLeft = 0), reply("end_turn", text("Ok"))))
        val session = session(script)
        assertEquals(EditEnd.OutOfSteps, session.ask("Shorter", "run1", 0L, emptySet()) {})
        assertEquals(1, session.changes.size)
        session.ask("And warmer", "run2", 0L, emptySet()) {}
        val sent = script.sent[1]
        // user, assistant, user (tool results + the follow-up): still alternating.
        assertEquals(3, sent.size)
        val last = sent[2].jsonObject.getValue("content").jsonArray.map { it.jsonObject.getValue("type").jsonPrimitive.content }
        assertEquals(listOf("tool_result", "text", "text"), last)
    }

    @Test
    fun `a failed first step leaves the conversation as it was`() = runTest {
        val script = Script(listOf(AiResult.Failed(AiFailure.Offline), reply("end_turn", text("Ok"))))
        val session = session(script)
        assertEquals(EditEnd.Failed(AiFailure.Offline), session.ask("Shorter", "run1", 0L, emptySet()) {})
        session.ask("Shorter", "run2", 0L, emptySet()) {}
        assertEquals(1, script.sent[1].size)
    }

    @Test
    fun `a reply cut short answers its calls without running them`() = runTest {
        val script = Script(listOf(reply("max_tokens", toolUse("t1", "trim_clip", """{"clip_id":"c1","keep_from_seconds":0,"keep_to_seconds":5}"""))))
        val session = session(script)
        assertTrue(session.ask("Shorter", "run", 0L, emptySet()) {} is EditEnd.Stopped)
        assertTrue(session.changes.isEmpty())
    }

    @Test
    fun `the app's own step cap ends a run that keeps calling tools`() = runTest {
        val speed = toolUse("t1", "set_clip_speed", """{"clip_id":"c1","speed":2}""")
        val script = Script(List(3) { reply("tool_use", speed) })
        assertEquals(EditEnd.OutOfSteps, session(script, maxSteps = 3).ask("Faster", "run", 0L, emptySet()) {})
        assertEquals(3, script.sent.size)
    }

    @Test
    fun `keeping only some changes replays them, and a change needing a dropped one is skipped`() = runTest {
        val split = toolUse("t1", "split_clip", """{"clip_id":"c1","at_seconds":4}""")
        val speedSecondPart = toolUse("t2", "set_clip_speed", """{"clip_id":"c5","speed":2}""")
        val look = toolUse("t3", "set_aspect_ratio", """{"ratio":"1:1"}""")
        val script = Script(listOf(reply("tool_use", split), reply("tool_use", speedSecondPart, look), reply("end_turn", text("Done"))))
        val session = session(script)
        session.ask("Edit", "run", 0L, emptySet()) {}
        assertEquals(3, session.changes.size)
        val all = replayChanges(session.original, session.changes, setOf(0, 1, 2), session.aliases)
        assertEquals(session.proposal, all.project)
        val withoutSplit = replayChanges(session.original, session.changes, setOf(1, 2), session.aliases)
        assertEquals(1, withoutSplit.skipped)
        assertEquals(1, withoutSplit.edit.actions.size)
        assertEquals(4, withoutSplit.project.clips.size)
    }
}
