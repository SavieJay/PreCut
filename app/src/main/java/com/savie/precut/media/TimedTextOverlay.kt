package com.savie.precut.media

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/** A pre-rendered text bitmap, centred on its anchor and visible only within its time range. */
@OptIn(UnstableApi::class)
class TimedTextOverlay(private val spec: TextSpec, private val bitmap: Bitmap) : BitmapOverlay() {

    private val visible: OverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(spec.anchorX, spec.anchorY)
        .build()

    private val hidden: OverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(spec.anchorX, spec.anchorY)
        .setAlphaScale(0f)
        .build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        if (spec.isVisibleAt(presentationTimeUs)) visible else hidden
}
