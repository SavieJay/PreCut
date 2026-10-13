package com.savie.precut.media

import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.FilterType
import com.savie.precut.model.OutputSize
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.outputSizeFor

/**
 * Pure description of the Media3 composition for a project. Kept free of Media3/Android types so
 * the Project → composition mapping can be unit-tested on the JVM.
 */
data class CompositionSpec(
    val items: List<ItemSpec>,
    val outputSize: OutputSize,
    /** Applied once to the whole composition, in timeline time. */
    val texts: List<TextSpec> = emptyList(),
)

/** Look shared by every item: colour effects, then user rotation, then fit into the frame. */
data class VisualSpec(
    val filter: List<FilterEffect> = emptyList(),
    /** Clockwise, 0/90/180/270. */
    val rotationDegrees: Int = 0,
)

sealed interface ItemSpec {
    val visual: VisualSpec

    data class Video(
        val uri: String,
        val sourceDurationMs: Long,
        val clipStartMs: Long,
        val clipEndMs: Long,
        val speed: Float = 1f,
        override val visual: VisualSpec = VisualSpec(),
    ) : ItemSpec

    data class Image(
        val uri: String,
        val durationMs: Long,
        override val visual: VisualSpec = VisualSpec(),
    ) : ItemSpec
}

/** A text overlay ready to render: pixel size in the output frame and a Media3 NDC anchor. */
data class TextSpec(
    val id: String,
    val text: String,
    val startUs: Long,
    val endUs: Long,
    /** -1 (left) .. 1 (right). */
    val anchorX: Float,
    /** -1 (bottom) .. 1 (top). */
    val anchorY: Float,
    val textSizePx: Float,
    val colorArgb: Int,
    val backgroundArgb: Int?,
)

/**
 * Text sizes are "sp on a [TEXT_REFERENCE_WIDTH_DP]-wide screen", scaled to the output's short
 * side, so the editor's on-screen ghost and the export agree regardless of resolution.
 */
const val TEXT_REFERENCE_WIDTH_DP = 360f

fun textSizePx(textSizeSp: Float, frameShortSidePx: Int): Float = textSizeSp * frameShortSidePx / TEXT_REFERENCE_WIDTH_DP

/** Maps a 0..1 position (y down) to Media3's normalized device coordinates (y up). */
fun toNdc(xNorm: Float, yNorm: Float): Pair<Float, Float> = (xNorm * 2f - 1f) to (1f - yNorm * 2f)

fun compositionSpecOf(project: Project): CompositionSpec {
    val outputSize = outputSizeFor(project.aspectRatio)
    return CompositionSpec(
        items = project.clips.map(::itemSpecOf),
        outputSize = outputSize,
        texts = project.textOverlays.filter { it.text.isNotBlank() }.map { textSpecOf(it, outputSize) },
    )
}

private fun itemSpecOf(clip: Clip): ItemSpec {
    val visual = VisualSpec(filterEffectsFor(clip.filter), clip.rotationDegrees)
    return when (clip.type) {
        ClipType.VIDEO -> ItemSpec.Video(
            uri = clip.uri,
            sourceDurationMs = clip.sourceDurationMs,
            clipStartMs = clip.trimStartMs,
            clipEndMs = clip.trimEndMs,
            speed = clip.speed,
            visual = visual,
        )
        ClipType.IMAGE -> ItemSpec.Image(uri = clip.uri, durationMs = clip.trimEndMs - clip.trimStartMs, visual = visual)
    }
}

private fun textSpecOf(overlay: TextOverlay, outputSize: OutputSize): TextSpec {
    val (x, y) = toNdc(overlay.xNorm, overlay.yNorm)
    return TextSpec(
        id = overlay.id,
        text = overlay.text,
        startUs = overlay.startMs * 1_000,
        endUs = overlay.endMs * 1_000,
        anchorX = x,
        anchorY = y,
        textSizePx = textSizePx(overlay.textSizeSp, minOf(outputSize.width, outputSize.height)),
        colorArgb = overlay.colorArgb,
        backgroundArgb = overlay.backgroundArgb,
    )
}

/** Whether a text overlay is on screen at composition time [presentationTimeUs]. */
fun TextSpec.isVisibleAt(presentationTimeUs: Long): Boolean = presentationTimeUs in startUs until endUs

/** A colour effect, described without Media3 types. */
sealed interface FilterEffect {
    /** -1..1 added to each channel. */
    data class Brightness(val amount: Float) : FilterEffect

    /** -1..1. */
    data class Contrast(val amount: Float) : FilterEffect

    /** -100..100. */
    data class Saturation(val amount: Float) : FilterEffect

    data object Grayscale : FilterEffect

    data class RgbScale(val red: Float, val green: Float, val blue: Float) : FilterEffect

    /** 4x4 column-major RGB matrix (OpenGL layout), as Media3's RgbMatrix expects. */
    data class Matrix(val values: List<Float>) : FilterEffect
}

fun filterEffectsFor(filter: FilterType): List<FilterEffect> = when (filter) {
    FilterType.NONE -> emptyList()
    FilterType.BRIGHTNESS -> listOf(FilterEffect.Brightness(0.12f))
    FilterType.CONTRAST -> listOf(FilterEffect.Contrast(0.3f))
    FilterType.SATURATION -> listOf(FilterEffect.Saturation(45f))
    FilterType.GRAYSCALE -> listOf(FilterEffect.Grayscale)
    FilterType.WARM -> listOf(FilterEffect.RgbScale(1.1f, 1.0f, 0.85f))
    FilterType.COOL -> listOf(FilterEffect.RgbScale(0.88f, 1.0f, 1.12f))
    FilterType.VINTAGE -> listOf(FilterEffect.Matrix(VINTAGE_MATRIX), FilterEffect.Contrast(-0.08f))
}

/**
 * Classic sepia (rows: R' = .393R + .769G + .189B, G' = .349R + .686G + .168B,
 * B' = .272R + .534G + .131B) blended 80/20 with the original, written column-major.
 */
val VINTAGE_MATRIX: List<Float> = run {
    val sepia = arrayOf(
        floatArrayOf(0.393f, 0.769f, 0.189f),
        floatArrayOf(0.349f, 0.686f, 0.168f),
        floatArrayOf(0.272f, 0.534f, 0.131f),
    )
    val blend = 0.8f
    val values = MutableList(16) { 0f }
    for (column in 0..2) {
        for (row in 0..2) {
            val identity = if (row == column) 1f else 0f
            values[column * 4 + row] = blend * sepia[row][column] + (1 - blend) * identity
        }
    }
    values[15] = 1f
    values
}
