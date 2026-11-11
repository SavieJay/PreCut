package com.savie.precut.media

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/**
 * One overlay shared by texts that are never on screen together (see [overlaySlots]), e.g. every
 * line of auto-captions: it shows whichever is visible now. Media3 re-uploads the texture only
 * when the bitmap changes, and between texts the last one stays, hidden.
 */
@OptIn(UnstableApi::class)
class TextSlotOverlay(texts: List<Pair<TextSpec, TimedTextOverlay>>) : BitmapOverlay() {

    private val sorted = texts.sortedBy { it.first.startUs }
    private val starts = LongArray(sorted.size) { sorted[it].first.startUs }
    private val hidden: OverlaySettings = StaticOverlaySettings.Builder().setAlphaScale(0f).build()
    private var last: Bitmap? = null

    init {
        require(sorted.isNotEmpty())
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        // Nothing showing: keep the last bitmap (hidden) so the texture isn't re-uploaded.
        val text = visibleAt(presentationTimeUs) ?: last?.let { return it } ?: sorted.first().second
        return text.getBitmap(presentationTimeUs).also { last = it }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        visibleAt(presentationTimeUs)?.getOverlaySettings(presentationTimeUs) ?: hidden

    override fun configure(videoSize: Size) {
        sorted.forEach { it.second.configure(videoSize) }
    }

    /** The text showing at [timeUs]: the last one starting at or before it, if it hasn't ended. */
    private fun visibleAt(timeUs: Long): TimedTextOverlay? {
        var index = starts.binarySearch(timeUs)
        if (index < 0) index = -index - 2
        // Texts in a slot don't overlap (empty ones are left out), so only this one can be showing.
        return sorted.getOrNull(index)?.takeIf { it.first.isVisibleAt(timeUs) }?.second
    }
}
