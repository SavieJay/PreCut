package com.savie.precut.model.sticker

import com.savie.precut.model.MAX_TEXT_SIZE_SP
import com.savie.precut.model.MIN_TEXT_SIZE_SP
import com.savie.precut.model.TextOverlay
import kotlinx.serialization.Serializable

/**
 * What a sticker shows (S-01, S-02). A sticker is a [TextOverlay] with [TextOverlay.sticker] set:
 * it keeps the text's place, size ([TextOverlay.textSizeSp] is the sticker's height), turn,
 * opacity, timing, keyframes and In/Out/Loop animations; the words and text styling are unused.
 */
@Serializable
data class Sticker(
    val kind: StickerKind,
    /** [StickerKind.EMOJI]: the emoji; otherwise the picture's Uri. */
    val value: String,
    /** Width / height of the picture. */
    val aspect: Float = 1f,
    /** A GIF whose frames play over time. */
    val animated: Boolean = false,
)

@Serializable
enum class StickerKind { EMOJI, IMAGE, DRAWING }

val TextOverlay.isSticker: Boolean get() = sticker != null

/** Sticker height bounds, in the units of [TextOverlay.textSizeSp] (sp on a 360 dp-wide frame). */
const val MIN_STICKER_SIZE_SP = 16f
const val MAX_STICKER_SIZE_SP = 720f
const val DEFAULT_STICKER_SIZE_SP = 96f
const val DEFAULT_STICKER_DURATION_MS = 3_000L

/** Size bounds for pinch-to-scale: a sticker's, or a text's. */
fun TextOverlay.sizeRange(): ClosedFloatingPointRange<Float> =
    if (isSticker) MIN_STICKER_SIZE_SP..MAX_STICKER_SIZE_SP else MIN_TEXT_SIZE_SP..MAX_TEXT_SIZE_SP

/**
 * A new sticker at [startMs], centred, [DEFAULT_STICKER_DURATION_MS] long; a full-frame drawing
 * ([fullFrameHeightSp]) covers the frame.
 */
fun stickerOverlay(id: String, sticker: Sticker, startMs: Long, fullFrameHeightSp: Float? = null): TextOverlay = TextOverlay(
    id = id,
    text = "",
    startMs = startMs,
    endMs = startMs + DEFAULT_STICKER_DURATION_MS,
    xNorm = 0.5f,
    yNorm = 0.5f,
    textSizeSp = fullFrameHeightSp ?: DEFAULT_STICKER_SIZE_SP,
    sticker = sticker,
)
