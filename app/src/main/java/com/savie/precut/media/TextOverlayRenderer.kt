package com.savie.precut.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.savie.precut.model.OutputSize
import javax.inject.Inject
import kotlin.math.ceil

/**
 * Draws a text overlay into a bitmap at output resolution: centred lines, wrapped at 90% of the
 * frame width, on an optional rounded background. The editor's on-screen ghost mirrors these
 * proportions ([TEXT_PADDING_RATIO], [TEXT_CORNER_RATIO]).
 */
class TextOverlayRenderer @Inject constructor() {

    fun render(spec: TextSpec, outputSize: OutputSize): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = spec.textSizePx
            color = spec.colorArgb
        }
        val maxWidth = (outputSize.width * 0.9f).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(spec.text, 0, spec.text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        val textWidth = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
        val padding = spec.textSizePx * TEXT_PADDING_RATIO
        val width = ceil(textWidth + 2 * padding).toInt().coerceAtLeast(1)
        val height = ceil(layout.height + 2 * padding).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        spec.backgroundArgb?.let { background ->
            val radius = spec.textSizePx * TEXT_CORNER_RATIO
            canvas.drawRoundRect(
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                radius,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background },
            )
        }
        // The layout is maxWidth wide with centred lines; shift it so the text block is centred.
        canvas.translate(padding - (maxWidth - textWidth) / 2f, padding)
        layout.draw(canvas)
        return bitmap
    }
}

const val TEXT_PADDING_RATIO = 0.25f
const val TEXT_CORNER_RATIO = 0.2f
