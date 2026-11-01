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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Project ⇄ JSON. Unknown keys are ignored and missing ones take model defaults, so drafts saved by
 * older or newer app versions still open. Each draft carries the [SCHEMA_VERSION] it was saved
 * with (Phase 23; none means 1), and older ones are migrated on read, step by step ([migrate]).
 */
object ProjectSerializer {
    /** The project JSON format this version writes. Add a step to [STEPS] whenever it changes. */
    const val SCHEMA_VERSION = 2

    private const val VERSION_KEY = "schemaVersion"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /** Version n's step upgrades JSON of version n to n + 1. */
    private val STEPS: Map<Int, (JsonObject) -> JsonObject> = mapOf(
        1 to ::legacyFilters,
    )

    fun encode(project: Project): String = versioned(json.encodeToJsonElement(Project.serializer(), project).jsonObject).toString()

    /** A project's JSON marked with the version it's written in (also a template's embedded project). */
    internal fun versioned(project: JsonObject): JsonObject = JsonObject(mapOf(VERSION_KEY to JsonPrimitive(SCHEMA_VERSION)) + project)

    /** Null if the JSON is corrupt. */
    fun decode(text: String): Project? = try {
        json.decodeFromJsonElement(Project.serializer(), migrate(json.parseToJsonElement(text)))
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * Upgrades a draft's JSON (also a template's embedded project) from the version it was saved
     * with to [SCHEMA_VERSION]. A draft from a newer app is read as it is, as far as it goes.
     */
    internal fun migrate(root: JsonElement): JsonElement {
        if (root !is JsonObject) return root
        var version = (root[VERSION_KEY] as? JsonPrimitive)?.intOrNull ?: 1
        var migrated: JsonObject = root
        while (version < SCHEMA_VERSION) {
            migrated = STEPS.getValue(version)(migrated)
            version++
        }
        return JsonObject(migrated - VERSION_KEY)
    }

    /** 1 → 2: Phase 4–7 clips stored `"filter": "WARM"`; Phase 8 stores `"look": {"filterId": "basic.warm"}`. */
    private fun legacyFilters(root: JsonObject): JsonObject {
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
