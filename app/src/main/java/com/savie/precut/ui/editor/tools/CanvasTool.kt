package com.savie.precut.ui.editor.tools

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.media.frame.CanvasPatterns
import com.savie.precut.model.frame.CanvasKind
import com.savie.precut.model.frame.CanvasPattern
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.ui.components.ColorDot
import com.savie.precut.ui.components.PillTab
import com.savie.precut.ui.components.PillTabs
import com.savie.precut.ui.components.formatHexColor

/** The design's canvas swatches (T-13b): black, white, cream, pink, yellow, teal, blue. */
private val SWATCHES = listOf(
    Color.Black, Color.White, Color(0xFFF3E6CF), Color(0xFFFF4D6D), Color(0xFFF2C94C), Color(0xFF16897A), Color(0xFF2F6FB8),
)

/** Compose blur per strength (Light … Max) for the tile previews. */
private val TILE_BLUR = listOf(1.5.dp, 3.dp, 5.dp, 8.dp)

/** What the Canvas panel can do. */
interface CanvasActions {
    fun onCanvas(canvas: ClipCanvas)
    fun onApplyAll()
    fun onPickImage()
}

/** Canvas (T-13): Colour, Blur or Image behind the clip, with "Apply to all". */
@Composable
fun CanvasTool(canvas: ClipCanvas, frameKey: ThumbnailKey?, loader: ThumbnailLoader, actions: CanvasActions, onDone: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(canvas.kind.ordinal) }
    ToolPanel(stringResource(R.string.tool_canvas), onDone, actions = { ApplyToAllButton(actions::onApplyAll) }) {
        PillTabs(
            tabs = listOf(R.string.canvas_colour, R.string.canvas_blur, R.string.canvas_image).map { PillTab(stringResource(it)) },
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.fillMaxWidth(),
        )
        when (CanvasKind.entries[tab]) {
            CanvasKind.COLOR -> ColourRow(canvas, actions)
            CanvasKind.BLUR -> BlurRow(canvas, frameKey, loader, actions)
            CanvasKind.IMAGE -> ImageRow(canvas, actions)
        }
    }
}

@Composable
private fun ColourRow(canvas: ClipCanvas, actions: CanvasActions) {
    var picking by remember { mutableStateOf(false) }
    val selected = canvas.colorArgb.takeIf { canvas.kind == CanvasKind.COLOR }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ColorDot(
            color = Color(canvas.colorArgb),
            selected = selected != null && SWATCHES.none { it.toArgb() == selected },
            description = stringResource(R.string.canvas_custom_colour),
            onClick = { picking = true },
            brush = Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.9f, 1f) }),
        )
        SWATCHES.forEach { color ->
            val argb = color.toArgb()
            ColorDot(color, selected = argb == selected, description = formatHexColor(argb), onClick = { actions.onCanvas(ClipCanvas(colorArgb = argb)) })
        }
    }
    if (picking) {
        ColorPickerSheet(
            initialArgb = canvas.colorArgb,
            recentColors = emptyList(),
            onPicked = { argb ->
                picking = false
                actions.onCanvas(ClipCanvas(colorArgb = argb))
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun BlurRow(canvas: ClipCanvas, frameKey: ThumbnailKey?, loader: ThumbnailLoader, actions: CanvasActions) {
    val frame by produceState<Bitmap?>(null, frameKey) { value = frameKey?.let { loader.load(it) } }
    val blurred = canvas.kind == CanvasKind.BLUR
    val labels = listOf(R.string.canvas_blur_light, R.string.canvas_blur_medium, R.string.canvas_blur_strong, R.string.canvas_blur_max)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(stringResource(R.string.canvas_none), R.drawable.ic_block, onClick = { actions.onCanvas(ClipCanvas()) }, selected = canvas.isDefault)
        labels.forEachIndexed { index, label ->
            val level = index + 1
            TileFrame(stringResource(label), selected = blurred && canvas.blur == level, onClick = { actions.onCanvas(ClipCanvas(kind = CanvasKind.BLUR, blur = level)) }) {
                frame?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().blur(TILE_BLUR[index])) }
            }
        }
    }
}

@Composable
private fun ImageRow(canvas: ClipCanvas, actions: CanvasActions) {
    val isImage = canvas.kind == CanvasKind.IMAGE
    val labels = mapOf(
        CanvasPattern.DOTS to R.string.canvas_pattern_dots,
        CanvasPattern.STRIPES to R.string.canvas_pattern_stripes,
        CanvasPattern.SAND to R.string.canvas_pattern_sand,
        CanvasPattern.DUSK to R.string.canvas_pattern_dusk,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(stringResource(R.string.canvas_from_gallery), R.drawable.ic_add_photo_alternate, onClick = actions::onPickImage, selected = isImage && canvas.imageUri != null)
        CanvasPattern.entries.forEach { pattern ->
            val tile = remember(pattern) { CanvasPatterns.draw(pattern, PATTERN_TILE_PX, PATTERN_TILE_PX).asImageBitmap() }
            TileFrame(stringResource(labels.getValue(pattern)), selected = isImage && canvas.pattern == pattern && canvas.imageUri == null, onClick = {
                actions.onCanvas(ClipCanvas(kind = CanvasKind.IMAGE, pattern = pattern))
            }) {
                Image(tile, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
        }
    }
}

private const val PATTERN_TILE_PX = 120
