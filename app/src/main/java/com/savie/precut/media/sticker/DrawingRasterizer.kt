package com.savie.precut.media.sticker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.savie.precut.model.OutputSize
import com.savie.precut.model.sticker.DrawStroke
import com.savie.precut.model.sticker.Drawing
import kotlin.math.max
import kotlin.math.roundToInt

/** Draws a [Drawing] into a transparent bitmap the shape of the frame (S-02 → a sticker). */
object DrawingRasterizer {
    /** The drawing at most [MAX_SIDE] px on its long side, in the frame's proportions. */
    fun rasterize(drawing: Drawing, frame: OutputSize): Bitmap {
        val scale = MAX_SIDE.toFloat() / max(frame.width, frame.height)
        val width = (frame.width * scale).roundToInt().coerceAtLeast(1)
        val height = (frame.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawing.strokes.forEach { draw(canvas, it, width, height) }
        return bitmap
    }

    private fun draw(canvas: Canvas, stroke: DrawStroke, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = stroke.widthNorm * minOf(width, height)
            color = stroke.colorArgb
            if (stroke.eraser) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val first = stroke.points.first()
        if (stroke.points.size == 1) {
            // A tap leaves a dot.
            paint.style = Paint.Style.FILL
            canvas.drawCircle(first.first * width, first.second * height, paint.strokeWidth / 2f, paint)
            return
        }
        val path = Path().apply {
            moveTo(first.first * width, first.second * height)
            stroke.points.drop(1).forEach { (x, y) -> lineTo(x * width, y * height) }
        }
        canvas.drawPath(path, paint)
    }

    private const val MAX_SIDE = 1_920
}
