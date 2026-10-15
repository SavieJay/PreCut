package com.savie.precut.media.frame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.savie.precut.model.frame.CanvasPattern
import kotlin.math.max
import kotlin.math.sqrt

/** The built-in canvas pictures (T-13c), drawn in code so there are no image assets to licence. */
object CanvasPatterns {

    /** [pattern] at [width] × [height]; its detail scales with the short side so tiles and export agree. */
    fun draw(pattern: CanvasPattern, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val unit = minOf(width, height) / 18f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (pattern) {
            CanvasPattern.DOTS -> {
                canvas.drawColor(0xFF1E2B3A.toInt())
                paint.color = 0xFF2F4A63.toInt()
                val cell = unit * 2f
                var y = 0f
                while (y < height + cell) {
                    var x = 0f
                    while (x < width + cell) {
                        canvas.drawCircle(x + cell * 0.25f, y + cell * 0.25f, cell * 0.12f, paint)
                        canvas.drawCircle(x + cell * 0.75f, y + cell * 0.7f, cell * 0.14f, paint)
                        x += cell
                    }
                    y += cell
                }
            }
            CanvasPattern.STRIPES -> {
                canvas.drawColor(0xFF3A2A22.toInt())
                paint.color = 0xFF4A3428.toInt()
                paint.strokeWidth = unit * 0.6f
                val step = unit * 1.2f * sqrt(2f)
                val span = max(width, height) * 2f
                var d = -span
                while (d < span) {
                    canvas.drawLine(d, height.toFloat(), d + height, 0f, paint)
                    d += step
                }
            }
            CanvasPattern.SAND -> gradient(canvas, paint, width, height, 0xFFE9D8B4.toInt(), 0xFFC7AE82.toInt(), diagonal = true)
            CanvasPattern.DUSK -> gradient(canvas, paint, width, height, 0xFF0B1A3A.toInt(), 0xFF6A3F66.toInt(), diagonal = false)
        }
        return bitmap
    }

    private fun gradient(canvas: Canvas, paint: Paint, width: Int, height: Int, from: Int, to: Int, diagonal: Boolean) {
        val endX = if (diagonal) width * 0.35f else 0f
        paint.shader = LinearGradient(0f, 0f, endX, height.toFloat(), from, to, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
