package com.savie.precut.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.savie.precut.model.OutputSize
import kotlin.math.min

/**
 * The project cover drawn over the whole frame until [CoverSpec.endUs], fitted on black like the
 * clips. The picture is loaded on first use, on the effects thread, via [load].
 */
@OptIn(UnstableApi::class)
class CoverOverlay(
    private val spec: CoverSpec,
    private val outputSize: OutputSize,
    private val load: () -> Bitmap?,
) : BitmapOverlay() {

    private val frame: Bitmap by lazy { compose(load()) }

    private val visible: OverlaySettings = StaticOverlaySettings.Builder().build()
    private val hidden: OverlaySettings = StaticOverlaySettings.Builder().setAlphaScale(0f).build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap = frame

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        if (presentationTimeUs < spec.endUs) visible else hidden

    private fun compose(picture: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        if (picture != null) {
            val scale = min(outputSize.width.toFloat() / picture.width, outputSize.height.toFloat() / picture.height)
            val w = picture.width * scale
            val h = picture.height * scale
            val left = (outputSize.width - w) / 2
            val top = (outputSize.height - h) / 2
            canvas.drawBitmap(picture, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return bitmap
    }
}
