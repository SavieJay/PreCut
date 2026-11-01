package com.savie.precut.model.ai.edit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The messages of the conversation the app keeps with Claude (docs/CLOUD_API.md, `POST /edit`).
 * Claude's replies are kept exactly as they came back (thinking included), and answered with one
 * user message holding a result for every tool call.
 */
object EditMessages {

    /** The person's [request], after the project's [brief] ("now" for a follow-up). */
    fun request(brief: JsonObject, request: String, followUp: Boolean): List<JsonObject> {
        val briefText = if (followUp) "The project now, with the changes kept so far:\n$brief" else brief.toString()
        return listOf(textBlock(briefText), textBlock(request))
    }

    fun user(blocks: List<JsonObject>): JsonObject = message("user", JsonArray(blocks))

    fun assistant(content: JsonArray): JsonObject = message("assistant", content)

    /** The tool calls in Claude's reply, in order. */
    fun toolCalls(content: JsonArray): List<EditToolCall> = content.mapNotNull { block ->
        val json = block as? JsonObject ?: return@mapNotNull null
        if (json.text("type") != "tool_use") return@mapNotNull null
        EditToolCall(json.text("id") ?: return@mapNotNull null, json.text("name").orEmpty(), json["input"] as? JsonObject ?: JsonObject(emptyMap()))
    }

    /** The words of Claude's reply. */
    fun text(content: JsonArray): String = content.mapNotNull { block ->
        (block as? JsonObject)?.takeIf { it.text("type") == "text" }?.text("text")
    }.joinToString("\n\n").trim()

    /** A tool's result for [callId]: [text] says what happened; [isError] when nothing was done. */
    fun toolResult(callId: String, text: String, isError: Boolean): JsonObject = buildJsonObject {
        put("type", "tool_result")
        put("tool_use_id", callId)
        put("content", text)
        if (isError) put("is_error", true)
    }

    /** The user message's blocks, or null if [message] is Claude's. */
    fun userBlocks(message: JsonObject): List<JsonObject>? =
        if (message.text("role") == "user") (message["content"] as? JsonArray)?.filterIsInstance<JsonObject>() else null

    private fun textBlock(text: String): JsonObject = buildJsonObject {
        put("type", "text")
        put("text", text)
    }

    private fun message(role: String, content: JsonElement): JsonObject = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
