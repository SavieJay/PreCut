package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.keyframe.Easing
import com.savie.precut.model.keyframe.EasingCurve
import com.savie.precut.model.keyframe.curveOf
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.hypot

/**
 * Keyframe easing (P-05): how values move from the keyframe at the playhead to the next one.
 * Presets, or Custom with a curve whose two handles can be dragged.
 */
@Composable
fun KeyframeEasingTool(easing: Easing, curve: EasingCurve?, onEasing: (Easing, EasingCurve?) -> Unit, onDone: () -> Unit) {
    ToolPanel(stringResource(R.string.keyframe_easing), onDone) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Easing.entries.forEach { option ->
                    val shown = if (option == Easing.CUSTOM) curve ?: curveOf(easing, null) else curveOf(option, null)
                    EasingTile(stringResource(option.label()), shown, selected = option == easing) {
                        onEasing(option, if (option == Easing.CUSTOM) shown else null)
                    }
                }
            }
            if (easing == Easing.CUSTOM) {
                CurveEditor(curve ?: EasingCurve.EASE_IN_OUT, onChanged = { onEasing(Easing.CUSTOM, it) })
                Text(stringResource(R.string.easing_hint), style = PreCutType.labelChip, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun EasingTile(label: String, curve: EasingCurve, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.width(60.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(shape)
                .background(if (selected) AccentContainer else Ink2)
                .then(if (selected) Modifier.border(2.dp, Accent, shape) else Modifier)
                .padding(12.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) { drawCurve(curve, if (selected) Accent else TextPrimary, 2.dp.toPx()) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) TextPrimary else TextSecondary, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** A 160 dp square graph: time across, progress up; drag either handle, released edits commit. */
@Composable
private fun CurveEditor(curve: EasingCurve, onChanged: (EasingCurve) -> Unit) {
    var live by remember(curve) { mutableStateOf(curve) }
    val current by rememberUpdatedState(live)
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Ink2)
            .padding(16.dp),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var handle = 0
                    detectDragGestures(
                        onDragStart = { at ->
                            // The nearer handle; graph y runs up.
                            val c = current
                            val first = Offset(c.x1 * size.width, (1f - c.y1) * size.height)
                            val second = Offset(c.x2 * size.width, (1f - c.y2) * size.height)
                            handle = if (hypot(at.x - first.x, at.y - first.y) <= hypot(at.x - second.x, at.y - second.y)) 1 else 2
                        },
                        onDragEnd = { onChanged(current) },
                    ) { change, _ ->
                        change.consume()
                        val x = change.position.x / size.width
                        val y = 1f - change.position.y / size.height
                        val c = current
                        live = if (handle == 1) EasingCurve.clamped(x, y, c.x2, c.y2) else EasingCurve.clamped(c.x1, c.y1, x, y)
                    }
                },
        ) {
            val c = live
            val first = Offset(c.x1 * size.width, (1f - c.y1) * size.height)
            val second = Offset(c.x2 * size.width, (1f - c.y2) * size.height)
            drawLine(TextSecondary, Offset(0f, size.height), first, 1.dp.toPx())
            drawLine(TextSecondary, Offset(size.width, 0f), second, 1.dp.toPx())
            drawCurve(c, Accent, 3.dp.toPx())
            drawCircle(Color.White, 8.dp.toPx(), first)
            drawCircle(Color.White, 8.dp.toPx(), second)
        }
    }
}

/** The easing as a graph filling the canvas (time across, progress up). */
private fun DrawScope.drawCurve(curve: EasingCurve, color: Color, width: Float) {
    val path = Path()
    val steps = 32
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val x = t * size.width
        val y = (1f - curve.ease(t)) * size.height
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width))
}

private fun Easing.label(): Int = when (this) {
    Easing.LINEAR -> R.string.easing_linear
    Easing.EASE_IN -> R.string.easing_in
    Easing.EASE_OUT -> R.string.easing_out
    Easing.EASE_IN_OUT -> R.string.easing_in_out
    Easing.CUSTOM -> R.string.easing_custom
}
