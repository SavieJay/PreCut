package com.savie.precut.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/**
 * The design's slider: 4 dp Ink3 track with an Accent fill and a 20 dp white knob. [bipolar]
 * draws a zero mark at the centre and fills from it. [bubble] labels the knob while dragging;
 * double-tapping the knob calls [onReset]. Values apply on release via [onValueChangeFinished].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreCutSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    bipolar: Boolean = false,
    bubble: ((Float) -> String)? = null,
    onReset: (() -> Unit)? = null,
    enabled: Boolean = true,
    /** Accent in panels; white over video (full screen). */
    fillColor: Color = Accent,
    knobSize: Dp = 20.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val currentOnReset by rememberUpdatedState(onReset)
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        thumb = {
            Box(Modifier.size(knobSize), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(knobSize)
                        .shadow(3.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { currentOnReset?.invoke() }) },
                )
                if (bubble != null && (dragged || pressed)) {
                    Text(
                        bubble(value),
                        style = PreCutType.labelChip.copy(fontSize = PreCutType.labelChip.fontSize * 0.85f).tabular(),
                        color = Ink0,
                        modifier = Modifier
                            .wrapContentSize(unbounded = true)
                            .offset(y = (-28).dp)
                            .background(TextPrimary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        },
        track = {
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                val trackHeight = 4.dp.toPx()
                val top = (size.height - trackHeight) / 2
                val radius = CornerRadius(trackHeight / 2)
                drawRoundRect(Ink3, Offset(0f, top), Size(size.width, trackHeight), radius)
                val from = if (bipolar) 0.5f else 0f
                val left = size.width * minOf(from, fraction)
                val right = size.width * maxOf(from, fraction)
                if (right > left) drawRoundRect(fillColor, Offset(left, top), Size(right - left, trackHeight), radius)
                if (bipolar) {
                    val markWidth = 2.dp.toPx()
                    drawRoundRect(TextSecondary, Offset(size.width / 2 - markWidth / 2, 0f), Size(markWidth, size.height), CornerRadius(markWidth / 2))
                }
            }
        },
    )
}
