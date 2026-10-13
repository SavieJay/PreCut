package com.savie.precut.data

import com.savie.precut.model.Project
import com.savie.precut.model.look.FilterCatalog
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * Project ⇄ JSON. Unknown keys are ignored and missing ones take model defaults, so drafts saved by
 * older or newer app versions still open. Older formats are migrated on read ([migrate]).
 */
object ProjectSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(project: Project): String = json.encodeToString(Project.serializer(), project)

    /** Null if the JSON is corrupt. */
    fun decode(text: String): Project? = try {
        json.decodeFromJsonElement(Project.serializer(), migrate(json.parseToJsonElement(text)))
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Phase 4–7 clips stored `"filter": "WARM"`; Phase 8 stores `"look": {"filterId": "basic.warm"}`. */
    private fun migrate(root: JsonElement): JsonElement {
        if (root !is JsonObject) return root
        val clips = root["clips"] as? JsonArray ?: return root
        val migrated = JsonArray(
            clips.map { clip ->
                if (clip !is JsonObject || "look" in clip) return@map clip
                val legacy = (clip["filter"] as? JsonPrimitive)?.contentOrNull
                val filterId = legacy?.let(FilterCatalog.LEGACY_IDS::get) ?: return@map clip
                JsonObject(clip - "filter" + ("look" to buildJsonObject { put("filterId", JsonPrimitive(filterId)) }))
            },
        )
        return JsonObject(root + ("clips" to migrated))
    }
}
