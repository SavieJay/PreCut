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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.RulerTick
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular
import kotlin.math.abs

/** A labelled mark under a slider's track, at slider value [value]. */
data class SliderTick(val value: Float, val label: String? = null)

/**
 * The design's slider: 4 dp Ink3 track with a fill and a white knob.
 * - [anchor] (a value) draws a zero mark there and fills from it (bipolar sliders, speed from 1x).
 * - [trackBrush] paints a gradient track instead of the fill (HSL).
 * - [ticks] draws marks and labels under the track; [snapTo] values attract the knob within 4 dp,
 *   with a haptic tick.
 * - [bubble] labels the knob while dragging; double-tapping the knob calls [onReset].
 * Values apply on release via [onValueChangeFinished].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreCutSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    anchor: Float? = null,
    bubble: ((Float) -> String)? = null,
    onReset: (() -> Unit)? = null,
    enabled: Boolean = true,
    /** Accent in panels; white over video (full screen). */
    fillColor: Color = Accent,
    knobSize: Dp = 20.dp,
    trackBrush: Brush? = null,
    ticks: List<SliderTick> = emptyList(),
    snapTo: List<Float> = emptyList(),
) {
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val currentOnReset by rememberUpdatedState(onReset)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    var snappedTo by remember { mutableIntStateOf(-1) }
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    fun fractionOf(v: Float) = ((v - valueRange.start) / span).coerceIn(0f, 1f)
    val measurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall.tabular().copy(color = TextSecondary)
    Slider(
        value = value,
        onValueChange = { raw ->
            // Within 4 dp of a snap value, stick to it (one haptic tick on arrival).
            val slopValue = if (widthPx > 0) with(density) { 4.dp.toPx() } / widthPx * span else 0f
            val index = snapTo.indexOfFirst { abs(it - raw) <= slopValue }
            if (index >= 0 && index != snappedTo) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            snappedTo = index
            onValueChange(if (index >= 0) snapTo[index] else raw)
        },
        modifier = modifier.height(if (ticks.any { it.label != null }) 52.dp else 40.dp).onSizeChanged { widthPx = it.width },
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
                        style = MaterialTheme.typography.labelMedium.tabular(),
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
            Canvas(Modifier.fillMaxWidth().height(if (ticks.any { it.label != null }) 30.dp else 14.dp)) {
                val trackHeight = if (trackBrush != null) 6.dp.toPx() else 4.dp.toPx()
                val top = 7.dp.toPx() - trackHeight / 2
                val radius = CornerRadius(trackHeight / 2)
                if (trackBrush != null) {
                    drawRoundRect(trackBrush, Offset(0f, top), Size(size.width, trackHeight), radius)
                } else {
                    drawRoundRect(Ink3, Offset(0f, top), Size(size.width, trackHeight), radius)
                    val from = anchor?.let(::fractionOf) ?: 0f
                    val to = fractionOf(value)
                    val left = size.width * minOf(from, to)
                    val right = size.width * maxOf(from, to)
                    if (right > left) drawRoundRect(fillColor, Offset(left, top), Size(right - left, trackHeight), radius)
                }
                ticks.forEach { tick ->
                    val x = size.width * fractionOf(tick.value)
                    drawRect(RulerTick, Offset(x - 0.5.dp.toPx(), top - 4.dp.toPx()), Size(1.dp.toPx(), trackHeight + 8.dp.toPx()))
                    tick.label?.let { label ->
                        val text = measurer.measure(label, tickStyle)
                        val textX = (x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
                        drawText(text, topLeft = Offset(textX, top + trackHeight + 6.dp.toPx()))
                    }
                }
                anchor?.let {
                    val markWidth = 2.dp.toPx()
                    val x = size.width * fractionOf(it)
                    drawRoundRect(
                        if (trackBrush != null) Ink0 else TextSecondary,
                        Offset(x - markWidth / 2, 0f), Size(markWidth, 14.dp.toPx()), CornerRadius(markWidth / 2),
                    )
                }
            }
        },
    )
}
