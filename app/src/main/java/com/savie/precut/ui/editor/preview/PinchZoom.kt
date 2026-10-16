package com.savie.precut.ui.editor.preview

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/** A view-only zoom of the preview (Phase 12): 1–4×, panned within the picture. */
@Stable
class PreviewZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    internal var size = IntSize.Zero

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    internal fun transform(pan: Offset, zoom: Float) {
        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        // The scaled picture may move by half of what it grew, so its edges never come inside.
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
    }

    private companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
    }
}

/** Pinch to zoom, drag to pan while zoomed, double-tap to reset; [onTap] for single taps. */
fun Modifier.pinchToZoom(zoom: PreviewZoom, onTap: () -> Unit): Modifier = this
    .onSizeChanged { zoom.size = it }
    .pointerInput(zoom) { detectTransformGestures { _, pan, gestureZoom, _ -> zoom.transform(pan, gestureZoom) } }
    .pointerInput(zoom) { detectTapGestures(onDoubleTap = { zoom.reset() }, onTap = { onTap() }) }

/** Draws the content zoomed and panned by [zoom]. */
fun Modifier.zoomed(zoom: PreviewZoom): Modifier = graphicsLayer {
    scaleX = zoom.scale
    scaleY = zoom.scale
    translationX = zoom.offset.x
    translationY = zoom.offset.y
}
