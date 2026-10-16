package com.savie.precut.ui.editor.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.Clip
import com.savie.precut.model.OutputSize
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.FrameGeometry
import com.savie.precut.model.frame.FrameSnaps
import com.savie.precut.model.frame.contentAspect
import kotlin.math.atan2
import kotlin.math.min

/** What the on-preview transform reports. */
interface ClipTransformActions {
    /** A gesture started moving the clip. */
    fun onStart()
    fun onLive(transform: ClipTransform)
    /** The gesture ended; null when nothing changed. */
    fun onEnd(transform: ClipTransform?)
}

/**
 * Transform frame for the selected main clip (T-15): white outline with corner marks and a rotate
 * handle. One finger on the clip moves it; two fingers anywhere pinch to scale and twist to rotate;
 * the handle turns and scales about the centre. Snaps to the centre lines and right angles.
 */
@Composable
fun ClipTransformOverlay(clip: Clip, outputSize: OutputSize, actions: ClipTransformActions, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val frame = FrameRect.fitting(outputSize, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        var live by remember(clip.id) { mutableStateOf<Pair<ClipTransform, FrameSnaps>?>(null) }
        var rotating by remember { mutableStateOf(false) }
        val shown = live?.first ?: clip.transform
        val box = clipBox(clip, shown, outputSize, frame)
        val currentClip by rememberUpdatedState(clip)
        val currentFrame by rememberUpdatedState(frame)
        val snapNorm = with(density) { 8.dp.toPx() } / min(frame.width, frame.height)

        fun update(next: Pair<ClipTransform, FrameSnaps>) {
            if (next.second != live?.second && next.second.any) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            live = next
            actions.onLive(next.first)
        }
        fun finish(changed: Boolean) {
            actions.onEnd(live?.first?.takeIf { changed })
            live = null
            rotating = false
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawClipFrame(box, live?.second, frame) }
                .pointerInput(clip.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val origin = currentClip.transform
                        val start = clipBox(currentClip, origin, outputSize, currentFrame)
                        val onClip = hitsText(down.position, start.center, start.size, start.rotationDegrees, 16.dp.toPx())
                        var pan = Offset.Zero
                        var zoom = 1f
                        var rotation = 0f
                        var started = false
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if ((onClip || pressed >= 2) && event.changes.any { it.positionChanged() }) {
                                if (!started) {
                                    started = true
                                    actions.onStart()
                                }
                                pan += event.calculatePan()
                                zoom *= event.calculateZoom()
                                if (pressed >= 2) rotation += event.calculateRotation()
                                rotating = pressed >= 2 && rotation != 0f
                                update(FrameGeometry.applyGesture(origin, pan.x / currentFrame.width, pan.y / currentFrame.height, zoom, rotation, snapNorm))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (started) finish(changed = true)
                    }
                },
        )
        // Below the bottom edge, turning with the clip: drag around the centre to rotate and scale.
        // Kept on the preview: a clip filling the frame would otherwise put it over Play.
        val handleAt = keepInside(
            box.center + rotate(Offset(0f, box.size.y / 2 + with(density) { 40.dp.toPx() }), box.rotationDegrees),
            constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), with(density) { 20.dp.toPx() },
        )
        FrameHandle(R.drawable.ic_rotate_right, stringResource(R.string.transform_rotate), handleAt) {
            Modifier.pointerInput(clip.id) {
                var origin = currentClip.transform
                var from = Offset.Zero
                var dragged = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        origin = currentClip.transform
                        val startBox = clipBox(currentClip, origin, outputSize, currentFrame)
                        from = handleAt - startBox.center
                        dragged = Offset.Zero
                        rotating = true
                        actions.onStart()
                    },
                    onDragEnd = { finish(changed = true) },
                    onDragCancel = { finish(changed = false) },
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                    val to = from + dragged
                    val zoom = if (from.getDistance() > 0f) to.getDistance() / from.getDistance() else 1f
                    val turn = Math.toDegrees((atan2(to.y, to.x) - atan2(from.y, from.x)).toDouble()).toFloat()
                    update(FrameGeometry.applyGesture(origin, 0f, 0f, zoom, turn, 0f))
                }
            }
        }
        if (rotating) AngleChip(shown.rotationDegrees, frame)
    }
}

/** The clip's outline in preview pixels: centre, size and clockwise turn. */
data class ClipBox(val center: Offset, val size: Offset, val rotationDegrees: Float)

internal fun clipBox(clip: Clip, transform: ClipTransform, output: OutputSize, frame: FrameRect): ClipBox {
    val placement = FrameGeometry.placement(clip.contentAspect(), 1f, output.width.toFloat(), output.height.toFloat(), clip.fit, transform)
    return ClipBox(
        center = frame.toPx(placement.centerX, placement.centerY),
        size = Offset(placement.width * frame.width, placement.height * frame.height),
        rotationDegrees = placement.rotationDegrees,
    )
}
