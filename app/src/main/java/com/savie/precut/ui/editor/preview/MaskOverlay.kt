package com.savie.precut.ui.editor.preview

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.Clip
import com.savie.precut.model.OutputSize
import com.savie.precut.model.frame.ClipMask
import com.savie.precut.model.frame.MaskGeometry
import com.savie.precut.model.frame.MaskShape
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

/** What the on-preview mask reports. */
interface MaskGestureActions {
    fun onStart()
    fun onLive(mask: ClipMask)
    /** The gesture ended; null when nothing changed. */
    fun onEnd(mask: ClipMask?)
}

/**
 * The selected clip's mask on the preview (P-03): its outline, moved with one finger, pinched to
 * resize and twisted to turn; handles for size (corner), turn (below) and feather (above).
 */
@Composable
fun MaskOverlay(clip: Clip, mask: ClipMask, outputSize: OutputSize, actions: MaskGestureActions, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val frame = FrameRect.fitting(outputSize, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val box = clipBox(clip, clip.transform, outputSize, frame)
        var live by remember(clip.id) { mutableStateOf<ClipMask?>(null) }
        val shown = live ?: mask
        val current by rememberUpdatedState(mask)
        val currentBox by rememberUpdatedState(box)
        val outline = MaskOutline.of(shown, box)
        val gap = with(density) { 28.dp.toPx() }
        val margin = with(density) { 20.dp.toPx() }
        fun inside(at: Offset) = keepInside(at, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), margin)

        fun update(next: ClipMask) {
            live = next
            actions.onLive(next)
        }
        fun finish() {
            actions.onEnd(live)
            live = null
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawMaskOutline(outline, shown.shape, shown.cornerRadius) }
                .pointerInput(clip.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val origin = current
                        var pan = Offset.Zero
                        var zoom = 1f
                        var turn = 0f
                        var started = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.positionChanged() }) {
                                if (!started) {
                                    started = true
                                    actions.onStart()
                                }
                                pan += event.calculatePan()
                                if (event.changes.count { it.pressed } >= 2) {
                                    zoom *= event.calculateZoom()
                                    turn += event.calculateRotation()
                                }
                                val delta = toPicture(pan, currentBox)
                                update(MaskGeometry.scaledAndTurned(MaskGeometry.moved(origin, delta.x, delta.y), zoom, turn))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (started) finish()
                    }
                },
        )
        if (shown.shape != MaskShape.LINEAR) {
            FrameHandle(R.drawable.ic_open_in_full, stringResource(R.string.mask_size), inside(outline.corner)) {
                Modifier.pointerInput(clip.id) {
                    var origin = current
                    var finger = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            origin = current
                            finger = MaskOutline.of(origin, currentBox).corner
                            actions.onStart()
                        },
                        onDragEnd = { finish() },
                        onDragCancel = { finish() },
                    ) { change, amount ->
                        change.consume()
                        finger += amount
                        // The finger's place in the mask's own axes sets the half sizes.
                        val start = MaskOutline.of(origin, currentBox)
                        val local = rotate(finger - start.center, -start.rotationDegrees)
                        val width = if (origin.shape == MaskShape.MIRROR) origin.width else 2f * abs(local.x) / currentBox.size.x
                        update(MaskGeometry.resized(origin, width, 2f * abs(local.y) / currentBox.size.y))
                    }
                }
            }
        }
        val below = inside(outline.center + rotate(Offset(0f, outline.halfSize.y + gap), outline.rotationDegrees))
        FrameHandle(R.drawable.ic_rotate_right, stringResource(R.string.mask_rotate), below) {
            Modifier.pointerInput(clip.id) {
                var from = Offset.Zero
                var dragged = Offset.Zero
                var origin = current
                detectDragGestures(
                    onDragStart = {
                        origin = current
                        from = below - MaskOutline.of(origin, currentBox).center
                        dragged = Offset.Zero
                        actions.onStart()
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                    val to = from + dragged
                    val degrees = Math.toDegrees((atan2(to.y, to.x) - atan2(from.y, from.x)).toDouble()).toFloat()
                    update(MaskGeometry.scaledAndTurned(origin, 1f, degrees))
                }
            }
        }
        val above = inside(outline.center + rotate(Offset(0f, -outline.halfSize.y - gap), outline.rotationDegrees))
        FrameHandle(R.drawable.ic_unfold_more, stringResource(R.string.mask_feather), above) {
            Modifier.pointerInput(clip.id) {
                var origin = current
                var dragged = 0f
                detectDragGestures(
                    onDragStart = {
                        origin = current
                        dragged = 0f
                        actions.onStart()
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                ) { change, amount ->
                    change.consume()
                    // Up softens: a drag of half the picture's short side is the whole range.
                    dragged -= amount.y
                    update(MaskGeometry.feathered(origin, origin.feather + dragged / (min(currentBox.size.x, currentBox.size.y) / 2f)))
                }
            }
        }
    }
}

/** A pan in preview px as picture fractions (along the picture's own axes). */
private fun toPicture(pan: Offset, box: ClipBox): Offset {
    val along = rotate(pan, -box.rotationDegrees)
    return Offset(along.x / box.size.x, along.y / box.size.y)
}
