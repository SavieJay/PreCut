package com.savie.precut.ui.editor.preview

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.savie.precut.model.Clip
import com.savie.precut.model.OutputSize
import com.savie.precut.model.frame.FrameGeometry
import com.savie.precut.model.frame.LayerOrientation
import com.savie.precut.model.frame.contentAspect
import com.savie.precut.model.frame.sourcePointAt

/**
 * The chroma key's eyedropper (P-04): touch and drag on the preview to pick the colour under the
 * finger from [frame] (the clip's source frame under the playhead, upright); a loupe shows it.
 * The colour is reported on release.
 */
@Composable
fun ChromaPickOverlay(clip: Clip, frame: Bitmap?, outputSize: OutputSize, onPicked: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val rect = FrameRect.fitting(outputSize, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        var finger by remember { mutableStateOf<Offset?>(null) }
        var colour by remember { mutableStateOf<Int?>(null) }
        val currentFrame by rememberUpdatedState(frame)
        val currentClip by rememberUpdatedState(clip)

        fun sample(at: Offset): Int? {
            val bitmap = currentFrame ?: return null
            val c = currentClip
            val placement = FrameGeometry.placement(c.contentAspect(), 1f, outputSize.width.toFloat(), outputSize.height.toFloat(), c.fit, c.transform)
            val orientation = LayerOrientation(c.mirrored, c.rotationDegrees, c.crop)
            val (x, y) = sourcePointAt(
                placement, outputSize.width.toFloat(), outputSize.height.toFloat(), orientation,
                (at.x - rect.left) / rect.width, (at.y - rect.top) / rect.height,
            ) ?: return null
            val px = (x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val py = (y * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            return bitmap.getPixel(px, py) or OPAQUE
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val at = finger ?: return@drawBehind
                    val picked = colour ?: return@drawBehind
                    // Above the finger so it isn't hidden by it.
                    val centre = Offset(at.x, at.y - 56.dp.toPx())
                    val radius = 26.dp.toPx()
                    drawCircle(Color(picked), radius, centre)
                    drawCircle(Color.Black.copy(alpha = 0.45f), radius, centre, style = Stroke(4.dp.toPx()))
                    drawCircle(Color.White, radius, centre, style = Stroke(2.dp.toPx()))
                    drawCircle(Color.White, 3.dp.toPx(), at)
                }
                .pointerInput(clip.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        finger = down.position
                        colour = sample(down.position)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull()?.let { change ->
                                finger = change.position
                                sample(change.position)?.let { colour = it }
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        colour?.let(onPicked)
                        finger = null
                    }
                },
        )
    }
}

private const val OPAQUE = 0xFF000000.toInt()
