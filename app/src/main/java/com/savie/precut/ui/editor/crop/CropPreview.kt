package com.savie.precut.ui.editor.crop

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.frame.CropRect
import com.savie.precut.model.frame.FrameGeometry
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Crop mode's picture (T-12b): a still of the clip at the playhead, mirrored and turned as in the
 * draft, with the crop rectangle on top. Corners resize (keeping a locked ratio), edges resize when
 * free, and dragging inside moves it.
 */
@Composable
fun CropPreview(
    frameKey: ThumbnailKey,
    loader: ThumbnailLoader,
    mirrored: Boolean,
    rotationDegrees: Int,
    crop: CropRect,
    lockedAspect: Float?,
    onChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source by produceState<Bitmap?>(null, frameKey) { value = loader.load(frameKey, cache = false) }
    val picture = remember(source, mirrored, rotationDegrees) { source?.let { upright(it, mirrored, rotationDegrees) } }
    BoxWithConstraints(modifier) {
        val bitmap = picture ?: return@BoxWithConstraints
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / bitmap.width, boxH / bitmap.height)
        val area = Rect(Offset((boxW - bitmap.width * scale) / 2, (boxH - bitmap.height * scale) / 2), Size(bitmap.width * scale, bitmap.height * scale))
        val density = LocalDensity.current
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            image,
            contentDescription = null,
            modifier = Modifier
                .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
                .size(with(density) { area.width.toDp() }, with(density) { area.height.toDp() }),
        )
        val currentCrop by rememberUpdatedState(crop)
        val currentAspect by rememberUpdatedState(lockedAspect)
        val currentArea by rememberUpdatedState(area)
        val pictureAspect = bitmap.width.toFloat() / bitmap.height
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(pictureAspect) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = currentCrop
                        val handle = handleAt(down.position, start.toPx(currentArea), currentAspect != null, 28f * density.density)
                            ?: return@awaitEachGesture
                        var total = Offset.Zero
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            total += change.positionChange()
                            change.consume()
                            onChange(FrameGeometry.dragCrop(start, handle, total.x / currentArea.width, total.y / currentArea.height, currentAspect, pictureAspect))
                        } while (change.pressed)
                    }
                },
        ) {
            drawCropFrame(crop.toPx(area))
        }
    }
}

/** [source] flipped left–right (when [mirrored]) and then turned clockwise, as Media3 does. */
private fun upright(source: Bitmap, mirrored: Boolean, rotationDegrees: Int): Bitmap {
    if (!mirrored && rotationDegrees % 360 == 0) return source
    val matrix = Matrix().apply {
        if (mirrored) postScale(-1f, 1f)
        postRotate(rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun CropRect.toPx(area: Rect) = Rect(
    area.left + left * area.width,
    area.top + top * area.height,
    area.left + right * area.width,
    area.top + bottom * area.height,
)

/** The handle under [point]: corners first, then edges (free ratio only), then inside to move. */
private fun handleAt(point: Offset, crop: Rect, locked: Boolean, slopPx: Float): FrameGeometry.CropHandle? {
    fun near(a: Float, b: Float) = abs(a - b) <= slopPx
    val left = near(point.x, crop.left)
    val right = near(point.x, crop.right)
    val top = near(point.y, crop.top)
    val bottom = near(point.y, crop.bottom)
    val withinX = point.x in crop.left - slopPx..crop.right + slopPx
    val withinY = point.y in crop.top - slopPx..crop.bottom + slopPx
    return when {
        left && top -> FrameGeometry.CropHandle.TOP_LEFT
        right && top -> FrameGeometry.CropHandle.TOP_RIGHT
        left && bottom -> FrameGeometry.CropHandle.BOTTOM_LEFT
        right && bottom -> FrameGeometry.CropHandle.BOTTOM_RIGHT
        !locked && left && withinY -> FrameGeometry.CropHandle.LEFT
        !locked && right && withinY -> FrameGeometry.CropHandle.RIGHT
        !locked && top && withinX -> FrameGeometry.CropHandle.TOP
        !locked && bottom && withinX -> FrameGeometry.CropHandle.BOTTOM
        crop.contains(point) -> FrameGeometry.CropHandle.MOVE
        else -> null
    }
}
