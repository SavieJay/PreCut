package com.savie.precut.model

import kotlinx.serialization.Serializable

@Serializable
data class TextOverlay(
    val id: String,
    val text: String,
    /** Timeline time. */
    val startMs: Long,
    val endMs: Long,
    /** 0..1 position in frame. */
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.8f,
    val textSizeSp: Float = 32f,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundArgb: Int? = null,
    /** Clockwise, any angle. */
    val rotationDegrees: Float = 0f,
    /** 0..1, applied to the text and its background. */
    val opacity: Float = 1f,
)

/** Text size bounds for the size slider and pinch-to-scale. */
const val MIN_TEXT_SIZE_SP = 8f
const val MAX_TEXT_SIZE_SP = 160f
