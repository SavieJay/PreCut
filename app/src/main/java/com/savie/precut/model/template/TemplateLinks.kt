package com.savie.precut.model.template

import com.savie.precut.model.mediaUris
import com.savie.precut.model.slotStartTimesMs
import com.savie.precut.model.totalDurationMs
import kotlinx.serialization.Serializable

/** Files in an app's own assets travel with every copy of the app. */
private const val ASSET_PREFIX = "file:///android_asset/"

/**
 * Template links (Phase 28): media in this template besides its slots and the app's own assets
 * (the maker's photos, stickers, canvas pictures) which a link can't carry. Music isn't counted:
 * it's left out of a shared template (the receiver adds their own).
 */
fun ProjectTemplate.ownMedia(): List<String> =
    project.copy(audioClips = emptyList()).mediaUris().filterNot { it.startsWith(SLOT_URI_PREFIX) || it.startsWith(ASSET_PREFIX) }.distinct()

/** The template as shared: without music, and null when it holds media of the maker's own. */
fun ProjectTemplate.forSharing(): ProjectTemplate? =
    if (ownMedia().isNotEmpty()) null else copy(project = project.copy(audioClips = emptyList(), cover = null))

/** One stretch of the main track on the website's drawing: its length, and whether it takes a clip of yours. */
@Serializable
data class SummaryPart(val ms: Long, val slot: Boolean)

/**
 * What the website's template page shows (it can't run the app's timeline maths): the length,
 * the main track's stretches, how many clips and texts to change. Stored beside the template;
 * the app ignores it when reading one.
 */
@Serializable
data class TemplateSummary(val lengthMs: Long, val parts: List<SummaryPart>, val slots: Int, val texts: Int)

fun ProjectTemplate.summary(): TemplateSummary {
    val total = project.totalDurationMs()
    val starts = project.slotStartTimesMs()
    val slotIds = slots.mapTo(HashSet()) { it.clipId }
    val parts = project.clips.mapIndexed { i, clip ->
        val end = starts.getOrNull(i + 1) ?: total
        SummaryPart((end - starts[i]).coerceAtLeast(0L), clip.id in slotIds)
    }
    return TemplateSummary(total, parts, slots.size, texts.size)
}

/** Shared templates' links: `https://<host>/t/<id>`, ids of 8 letters and digits. */
object TemplateLinks {
    private val ID = Regex("[A-Za-z0-9]{8}")

    fun link(host: String, id: String): String = "https://$host/t/$id"

    /** The id in [url] when it's a template link of [host], else null. */
    fun idOf(url: String, host: String): String? {
        val match = Regex("^https://${Regex.escape(host)}/t/([A-Za-z0-9]{8})/?(?:[?#].*)?$").find(url) ?: return null
        return match.groupValues[1].takeIf { ID.matches(it) }
    }

    /** The local id a received template is kept under. */
    fun localId(id: String): String = "shared-$id"
}
