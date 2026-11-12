package com.savie.precut.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

/** Glass.Panel (docked panels, cards) and Glass.Raised (sheets, menus, dialogs, the dock). */
enum class GlassLevel { Panel, Raised }

private val Sheen = Brush.linearGradient(
    0f to GlassSheenTop,
    0.45f to GlassSheenBottom.copy(alpha = 0.035f),
    1f to GlassSheenBottom,
    start = Offset.Zero,
    end = Offset.Infinite,
)

/**
 * The 2026 glass surface (RD-TOK "Glass, refined"): a translucent fill under a top-left sheen,
 * a white hairline, a 1 dp inner highlight along the top and a soft shadow. Backdrop blur isn't
 * drawn: Compose can't blur what's behind a surface (RenderEffect blurs a view's own content), so
 * the fill carries the look on every Android version. [solid] is for surfaces with busy content
 * behind them. Never put glass over the video preview or the full-screen player: those surfaces
 * use [OnVideoChip] or the dialogs' solid fill.
 */
fun Modifier.glass(shape: Shape, level: GlassLevel = GlassLevel.Panel, solid: Boolean = false): Modifier {
    val fill = when {
        solid -> GlassSolid
        level == GlassLevel.Raised -> GlassRaisedFill
        else -> GlassFill
    }
    val elevation = if (level == GlassLevel.Raised) 20.dp else 12.dp
    return this
        .shadow(elevation, shape, clip = false, ambientColor = Color.Transparent, spotColor = GlassShadow)
        .clip(shape)
        .background(fill)
        .background(Sheen)
        .border(1.dp, GlassLine, shape)
        .drawWithContent {
            drawContent()
            val line = 1.dp.toPx()
            drawLine(GlassHighlight, Offset(0f, line / 2), Offset(size.width, line / 2), line)
        }
}

/** A row or quiet icon button on glass: white 6% with a 10% hairline. */
fun Modifier.glassRow(shape: Shape): Modifier = clip(shape).background(RowFill).border(1.dp, RowLine, shape)

/**
 * The 2026 screen background: Base 0 with two soft pink glows, one behind the upper middle and one
 * at the bottom right (RD frames' `.rp` background). For screens without video; the editor keeps
 * plain Base 0 so footage stays neutral.
 */
fun Modifier.afterglowBackground(): Modifier = background(Ink0).drawBehind {
    glow(center = Offset(size.width * 0.5f, size.height * 0.26f), rx = size.width * 0.7f, ry = size.height * 0.34f, alpha = 0.20f)
    glow(center = Offset(size.width, size.height), rx = size.width * 0.6f, ry = size.height * 0.26f, alpha = 0.16f)
}

/** An elliptical glow: a circular radial gradient stretched to [rx] × [ry] around [center]. */
private fun DrawScope.glow(center: Offset, rx: Float, ry: Float, alpha: Float) {
    if (rx <= 0f || ry <= 0f) return
    scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
        drawCircle(
            Brush.radialGradient(0f to Accent.copy(alpha = alpha), 0.7f to Accent.copy(alpha = 0f), center = center, radius = rx),
            radius = rx,
            center = center,
        )
    }
}
