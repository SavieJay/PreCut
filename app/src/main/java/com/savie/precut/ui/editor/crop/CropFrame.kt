package com.savie.precut.ui.editor.crop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Dims outside [crop], then draws its 1.5 dp border, thirds grid and 4 dp corner marks (T-12b). */
fun DrawScope.drawCropFrame(crop: Rect) {
    val dim = Color.Black.copy(alpha = 0.62f)
    drawRect(dim, Offset.Zero, Size(size.width, crop.top))
    drawRect(dim, Offset(0f, crop.bottom), Size(size.width, size.height - crop.bottom))
    drawRect(dim, Offset(0f, crop.top), Size(crop.left, crop.height))
    drawRect(dim, Offset(crop.right, crop.top), Size(size.width - crop.right, crop.height))

    val grid = Color.White.copy(alpha = 0.55f)
    val hair = 1.dp.toPx()
    for (i in 1..2) {
        val x = crop.left + crop.width * i / 3
        val y = crop.top + crop.height * i / 3
        drawLine(grid, Offset(x, crop.top), Offset(x, crop.bottom), hair)
        drawLine(grid, Offset(crop.left, y), Offset(crop.right, y), hair)
    }
    drawRect(Color.White, crop.topLeft, crop.size, style = Stroke(1.5.dp.toPx()))

    // The marks sit just outside the border, like the design's -4 dp offset.
    val thick = 4.dp.toPx()
    val arm = 22.dp.toPx()
    listOf(
        Triple(crop.topLeft, 1f, 1f),
        Triple(crop.topRight, -1f, 1f),
        Triple(crop.bottomLeft, 1f, -1f),
        Triple(crop.bottomRight, -1f, -1f),
    ).forEach { (corner, sx, sy) ->
        val c = Offset(corner.x - sx * thick / 2, corner.y - sy * thick / 2)
        drawLine(Color.White, c, Offset(c.x + sx * arm, c.y), thick, cap = StrokeCap.Square)
        drawLine(Color.White, c, Offset(c.x, c.y + sy * arm), thick, cap = StrokeCap.Square)
    }
}
