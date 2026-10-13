package com.savie.precut.data

import com.savie.precut.model.Project
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Project ⇄ JSON. Unknown keys are ignored and missing ones take model defaults, so drafts saved by
 * older or newer app versions still open.
 */
object ProjectSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(project: Project): String = json.encodeToString(Project.serializer(), project)

    /** Null if the JSON is corrupt. */
    fun decode(text: String): Project? = try {
        json.decodeFromString(Project.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
