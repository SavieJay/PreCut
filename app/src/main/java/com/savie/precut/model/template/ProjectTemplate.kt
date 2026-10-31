package com.savie.precut.model.template

import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.Project
import com.savie.precut.model.clipById
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.timelineDurationMs
import kotlinx.serialization.Serializable

/** The template format's version; a template with a higher one needs a newer PreCut. */
const val TEMPLATE_SCHEMA = 1

/** A slot's placeholder Uri, by its place in the fill order. */
const val SLOT_URI_PREFIX = "template://slot/"

/** A short video is slowed to fill its slot down to this speed; below it, it stays shorter. */
const val MIN_FIT_SPEED = 0.5f

@Serializable
enum class TemplateCategory { TRAVEL, SOCIAL, BUSINESS, CELEBRATION, SLIDESHOW, VLOG, OTHER }

/** A clip or overlay that takes the user's media. */
@Serializable
data class TemplateSlot(val clipId: String)

/** A text the user is expected to change (every text stays editable either way). */
@Serializable
data class TemplateText(val textId: String)

/** Which template a project was made from. */
@Serializable
data class TemplateRef(val id: String, val title: String)

/**
 * An editable project recipe: [project] with its [slots]' media as placeholders. Using it gives a
 * normal project ([fill]), so everything the template sets stays editable. [credits] name the
 * licences of any bundled assets.
 */
@Serializable
data class ProjectTemplate(
    val id: String,
    val title: String,
    val project: Project,
    /** In fill order: by start time on the timeline. */
    val slots: List<TemplateSlot>,
    val texts: List<TemplateText> = emptyList(),
    val category: TemplateCategory = TemplateCategory.OTHER,
    val credits: List<String> = emptyList(),
    val schema: Int = TEMPLATE_SCHEMA,
    val createdAt: Long = 0L,
)

/** A picked file for a slot. */
data class TemplatePick(val uri: String, val info: MediaInfo)

/** A filled template: the new [project]; [ignoredPicks] beyond the slots, [slowed] videos slowed to fit, [shorter] ones still short. */
data class FilledTemplate(val project: Project, val ignoredPicks: Int, val slowed: Int, val shorter: Int)

/**
 * This project as a template: [slotIds] (clips or overlays) become placeholders in timeline order,
 * [textIds] are marked as the texts to change, and the cover goes. Other media keep their Uris:
 * storing a template copies them in.
 */
fun Project.toTemplate(id: String, title: String, category: TemplateCategory, slotIds: Set<String>, textIds: Set<String>, nowMs: Long): ProjectTemplate {
    val mainStarts = clips.zip(clipStartTimesMs()) { clip, start -> Triple(clip.id, start, 0) }
    val overlayStarts = overlays.map { Triple(it.id, it.timelineStartMs, 1) }
    val ordered = (mainStarts + overlayStarts).filter { it.first in slotIds }.sortedWith(compareBy({ it.second }, { it.third })).map { it.first }
    val placeholders = ordered.withIndex().associate { (index, clipId) -> clipId to SLOT_URI_PREFIX + index }
    // A slot's matte and noise profile belong to the media it held, so a filled slot starts without them.
    fun Clip.asSlot() = placeholders[this.id]?.let { copy(uri = it, reversedFrom = null, cutout = null, noiseReduction = null) } ?: this
    val recipe = copy(
        id = id,
        name = title,
        clips = clips.map { it.asSlot() },
        overlays = overlays.map { it.copy(clip = it.clip.asSlot()) },
        cover = null,
        coverInExport = false,
        template = null,
        createdAt = nowMs,
        updatedAt = nowMs,
    )
    return ProjectTemplate(
        id = id,
        title = title,
        project = recipe,
        slots = ordered.map(::TemplateSlot),
        texts = textOverlays.filter { it.id in textIds }.map { TemplateText(it.id) },
        category = category,
        createdAt = nowMs,
    )
}

/**
 * A new project from this template: [picks] fill the slots in order (repeating when there are
 * fewer), through Replace, so each slot keeps its look, motion, transition and length. A video
 * shorter than its slot is slowed to fill it, down to [MIN_FIT_SPEED].
 */
fun ProjectTemplate.fill(picks: List<TemplatePick>, projectId: String, nowMs: Long): FilledTemplate {
    require(picks.isNotEmpty()) { "Nothing to fill the template with" }
    var project = this.project.copy(id = projectId, name = "", createdAt = nowMs, updatedAt = nowMs, template = TemplateRef(id, title))
    var slowed = 0
    var shorter = 0
    slots.forEachIndexed { index, slot ->
        val slotLength = project.clipById(slot.clipId)?.timelineDurationMs() ?: return@forEachIndexed
        val pick = picks[index % picks.size]
        project = EditReducer.reduce(project, EditAction.ReplaceClip(slot.clipId, pick.uri, pick.info))
        val placed = project.clipById(slot.clipId) ?: return@forEachIndexed
        if (placed.type != ClipType.VIDEO || placed.speedCurve != null || placed.timelineDurationMs() >= slotLength - 1) return@forEachIndexed
        val fit = placed.sourceDurationMs.toFloat() / slotLength
        if (fit < MIN_FIT_SPEED) {
            shorter++
            return@forEachIndexed
        }
        project = EditReducer.reduce(
            project,
            EditAction.Batch(listOf(EditAction.TrimClip(slot.clipId, 0L, placed.sourceDurationMs), EditAction.SetSpeed(slot.clipId, fit))),
        )
        slowed++
    }
    return FilledTemplate(project, (picks.size - slots.size).coerceAtLeast(0), slowed, shorter)
}
