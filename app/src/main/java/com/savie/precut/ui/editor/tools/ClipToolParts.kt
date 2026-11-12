package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.RowLine

/** The 2026 icon-only Reset in a tool panel's header (RD-T02, T07: `restart_alt`, no label). */
@Composable
internal fun ResetIconButton(onClick: () -> Unit, enabled: Boolean = true, description: String = stringResource(R.string.adjust_reset)) {
    PreCutIconButton(R.drawable.ic_restart_alt, description, onClick, enabled = enabled)
}

/** The design's `.graph`: a dark box with 18 dp corners, a hairline and a faint 4 × 4 grid. */
internal fun Modifier.graphBox(): Modifier =
    clip(GraphShape)
        .background(GraphFill)
        .border(1.dp, RowLine, GraphShape)
        .drawBehind {
            for (i in 1..3) {
                val x = size.width * i / 4f
                val y = size.height * i / 4f
                drawLine(GraphGrid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                drawLine(GraphGrid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
        }

/** A graph point (`.gp`): 20 dp, white (pink when [active]) with a 3 dp dark edge. */
internal fun DrawScope.drawGraphPoint(center: Offset, active: Boolean, diameter: Dp = 20.dp) {
    val radius = diameter.toPx() / 2f
    drawCircle(Ink0, radius, center)
    drawCircle(if (active) Accent else Color.White, radius - 3.dp.toPx(), center)
}

private val GraphShape = RoundedCornerShape(18.dp)
private val GraphFill = Color.Black.copy(alpha = 0.3f)
private val GraphGrid = Color.White.copy(alpha = 0.06f)
