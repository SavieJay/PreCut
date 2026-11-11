package com.savie.precut.evals

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.core.jsonMapper
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.ai.edit.EditStepReply
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 * One AI edit step for the evals, done as the cloud function does it (same system prompt, tools,
 * model, effort and caching; supabase/functions/_shared/edit_agent.ts) but straight to Anthropic's
 * API, so the evals need no phone, Play Integrity or deployed server. Claude's replies are kept as
 * the SDK's messages and sent back unchanged (thinking included); the app's session sees their
 * text and tool calls.
 */
class ClaudeEvalStep(private val client: AnthropicClient, private val model: String) {
    private val shared = File("../supabase/functions/_shared")
    private val systemPrompt: String = Regex("export const SYSTEM_PROMPT =\\s*`([\\s\\S]*?)`;")
        .find(File(shared, "edit_agent.ts").readText())?.groupValues?.get(1) ?: error("SYSTEM_PROMPT not found")
    private val tools: List<Tool> = Json.parseToJsonElement(File(shared, "edit_tools.json").readText()).jsonArray.map { it.jsonObject.toTool() }
    private val replies = mutableListOf<Message>()

    /** Usage across all steps, for the report. */
    var inputTokens = 0L
        private set
    var outputTokens = 0L
        private set

    fun step(messages: JsonArray): AiResult<EditStepReply> {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16_000L)
            .system(systemPrompt)
            .tools(tools.map(com.anthropic.models.messages.ToolUnion::ofTool))
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
            .cacheControl(CacheControlEphemeral.builder().build())
        var assistant = 0
        messages.forEach { element ->
            val message = element.jsonObject
            if (message["role"]?.jsonPrimitive?.contentOrNull == "assistant") {
                params.addMessage(replies[assistant++])
            } else {
                params.addUserMessageOfContentBlockParams(message.getValue("content").jsonArray.map { it.jsonObject.toBlock() })
            }
        }
        val reply = client.messages().create(params.build())
        replies += reply
        inputTokens += reply.usage().inputTokens()
        outputTokens += reply.usage().outputTokens()
        val content = buildJsonArray {
            reply.content().forEach { block ->
                block.text().ifPresent { text -> add(buildJsonObject { put("type", "text"); put("text", text.text()) }) }
                block.toolUse().ifPresent { use ->
                    add(buildJsonObject {
                        put("type", "tool_use")
                        put("id", use.id())
                        put("name", use.name())
                        put("input", Json.parseToJsonElement(jsonMapper().writeValueAsString(use._input())))
                    })
                }
            }
        }
        return AiResult.Ok(EditStepReply(content, reply.stopReason().map { it.asString() }.orElse("end_turn"), stepsLeft = 8, editRunsLeft = 99))
    }

    private fun JsonObject.toBlock(): ContentBlockParam = when (this["type"]?.jsonPrimitive?.contentOrNull) {
        "tool_result" -> ContentBlockParam.ofToolResult(
            ToolResultBlockParam.builder()
                .toolUseId(getValue("tool_use_id").jsonPrimitive.content)
                .content(getValue("content").jsonPrimitive.content)
                .isError(this["is_error"]?.jsonPrimitive?.booleanOrNull == true)
                .build(),
        )
        else -> ContentBlockParam.ofText(TextBlockParam.builder().text(getValue("text").jsonPrimitive.content).build())
    }

    private fun JsonObject.toTool(): Tool {
        val schema = getValue("input_schema").jsonObject
        val properties = Tool.InputSchema.Properties.builder()
        schema.getValue("properties").jsonObject.forEach { (name, value) -> properties.putAdditionalProperty(name, JsonValue.from(plain(value))) }
        return Tool.builder()
            .name(getValue("name").jsonPrimitive.content)
            .description(getValue("description").jsonPrimitive.content)
            // As the server sends them: the tools with many optional values aren't strict (edit_tools.json).
            .strict(this["strict"]?.jsonPrimitive?.booleanOrNull == true)
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(properties.build())
                    .required(schema.getValue("required").jsonArray.map { it.jsonPrimitive.content })
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build(),
            )
            .build()
    }

    /** kotlinx JSON as plain Kotlin values, for the SDK's JsonValue. */
    private fun plain(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonObject -> element.mapValues { plain(it.value) }
        is JsonArray -> element.map(::plain)
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull
            element.longOrNull != null -> element.longOrNull
            else -> element.doubleOrNull
        }
    }
}
