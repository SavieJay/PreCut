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
)
