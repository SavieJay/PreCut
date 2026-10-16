package com.savie.precut.ui.editor.preview

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.savie.precut.model.frame.ClipMask
import com.savie.precut.model.frame.MaskShape
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A mask's outline in preview px: centre, half size and clockwise turn (the clip's plus the mask's). */
internal data class MaskOutline(val center: Offset, val halfSize: Offset, val rotationDegrees: Float) {

    /** The size handle: the top-right corner, turned with the mask. */
    val corner: Offset get() = center + rotate(Offset(halfSize.x, -halfSize.y), rotationDegrees)

    companion object {
        fun of(mask: ClipMask, box: ClipBox): MaskOutline {
            val offset = rotate(Offset(mask.centerX * box.size.x, mask.centerY * box.size.y), box.rotationDegrees)
            return MaskOutline(
                center = box.center + offset,
                halfSize = Offset(mask.width * box.size.x / 2f, mask.height * box.size.y / 2f),
                rotationDegrees = box.rotationDegrees + mask.rotationDegrees,
            )
        }
    }
}

/** White outline with a soft dark edge, shaped like the shader's mask. */
internal fun DrawScope.drawMaskOutline(outline: MaskOutline, shape: MaskShape, cornerRadius: Float) {
    val hw = outline.halfSize.x
    val hh = outline.halfSize.y
    val path = Path()
    val reach = size.maxDimension * 2f
    when (shape) {
        MaskShape.LINEAR -> {
            path.moveTo(-reach, 0f)
            path.lineTo(reach, 0f)
        }
        MaskShape.MIRROR -> {
            path.moveTo(-reach, -hh)
            path.lineTo(reach, -hh)
            path.moveTo(-reach, hh)
            path.lineTo(reach, hh)
        }
        MaskShape.CIRCLE -> path.addOval(Rect(-hw, -hh, hw, hh))
        MaskShape.RECTANGLE -> {
            val r = cornerRadius * min(hw, hh)
            path.addRoundRect(RoundRect(Rect(-hw, -hh, hw, hh), CornerRadius(r, r)))
        }
        MaskShape.HEART -> {
            // Tip at the bottom, lobes reaching the top edge (y down).
            path.moveTo(0f, hh)
            path.cubicTo(-hw * 0.5f, hh * 0.55f, -hw * 1.05f, hh * 0.05f, -hw * 0.98f, -hh * 0.35f)
            path.cubicTo(-hw * 0.92f, -hh * 1.02f, -hw * 0.12f, -hh * 1.08f, 0f, -hh * 0.5f)
            path.cubicTo(hw * 0.12f, -hh * 1.08f, hw * 0.92f, -hh * 1.02f, hw * 0.98f, -hh * 0.35f)
            path.cubicTo(hw * 1.05f, hh * 0.05f, hw * 0.5f, hh * 0.55f, 0f, hh)
            path.close()
        }
        MaskShape.STAR -> {
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) 1f else STAR_INNER
                val angle = Math.toRadians(-90.0 + i * 36.0)
                val x = (cos(angle) * radius).toFloat() * hw
                val y = (sin(angle) * radius).toFloat() * hh
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
    }
    withTransform({
        translate(outline.center.x, outline.center.y)
        rotate(outline.rotationDegrees, Offset.Zero)
    }) {
        drawPath(path, Color.Black.copy(alpha = 0.45f), style = Stroke(width = 3.dp.toPx()))
        drawPath(path, Color.White, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** Inner points of the star outline, as a fraction of its outer radius (the shader's `rf`). */
private const val STAR_INNER = 0.45f
