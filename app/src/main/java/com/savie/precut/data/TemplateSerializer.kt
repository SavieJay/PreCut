package com.savie.precut.data

import com.savie.precut.model.template.ProjectTemplate
import com.savie.precut.model.template.TEMPLATE_SCHEMA
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Templates as JSON, like projects ([ProjectSerializer]): unknown keys ignored, defaults left out. */
object TemplateSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(template: ProjectTemplate): String = json.encodeToString(ProjectTemplate.serializer(), template)

    /** Null when the JSON is corrupt, or the template needs a newer PreCut (schema above [TEMPLATE_SCHEMA]). */
    fun decode(text: String): ProjectTemplate? = try {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return null
        val schema = root["schema"]?.jsonPrimitive?.intOrNull ?: 1
        if (schema > TEMPLATE_SCHEMA) {
            null
        } else {
            val project = root["project"]?.let(ProjectSerializer::migrate)
            val upgraded = if (project == null) root else JsonObject(root + ("project" to project))
            json.decodeFromJsonElement(ProjectTemplate.serializer(), upgraded)
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
