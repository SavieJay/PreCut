package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.StickerBar
import com.savie.precut.ui.theme.TextLaneBar
import com.savie.precut.model.sticker.Sticker
import com.savie.precut.model.sticker.StickerKind
import kotlin.math.roundToLong

/**
 * Text overlays as bars, stacked into rows so overlapping texts don't hide each other (CapCut).
 * Drag any bar sideways to move it in time (it gets selected); the selected bar also has handles
 * for its start and end. Taps select (or toggle, in multi-select).
 */
@Composable
fun TextRows(
    lanes: List<List<TextOverlay>>,
    selectedTextId: String?,
    multiIds: Set<String>,
    pxPerMs: Float,
    widthPx: Float,
    rowHeight: Dp,
    rowGap: Dp,
    snap: (ownerId: String, startMs: Long, endMs: Long, moveMs: Long) -> Long,
    onSelect: (String) -> Unit,
    onTiming: (textId: String, startMs: Long, endMs: Long) -> Unit,
    /** Sticker rows (S-01b): their own colour, glyph and labels, and no empty row. */
    stickers: Boolean = false,
) {
    val density = LocalDensity.current
    val totalMs = (widthPx / pxPerMs).roundToLong()
    val placeholder = stringResource(R.string.text_placeholder)
    val stickerLabels = StickerLabels(stringResource(R.string.sticker_image), stringResource(R.string.sticker_gif), stringResource(R.string.sticker_drawing))
    Column {
        // At least one (empty) text row keeps the lane's place under the clips; sticker rows
        // sit above it, each followed by a gap.
        (if (stickers) lanes else lanes.ifEmpty { listOf(emptyList()) }).forEachIndexed { index, lane ->
            if (index > 0) Spacer(Modifier.height(rowGap))
            Box(Modifier.width(with(density) { widthPx.toDp() }).height(rowHeight)) {
                lane.forEach { overlay ->
                    // key(): each bar keeps its state and in-flight gesture across recompositions.
                    key(overlay.id) {
                        fun range(drag: BarDrag) = TimelineGeometry.dragTextRange(
                            overlay.startMs, overlay.endMs, drag.moveMs, drag.startMs, drag.endMs, totalMs,
                        )
                        TimelineBar(
                            id = overlay.id,
                            label = overlay.sticker?.let { stickerLabels.of(it) } ?: overlay.text.ifBlank { placeholder },
                            color = if (stickers) StickerBar else TextLaneBar,
                            selected = overlay.id == selectedTextId,
                            multiSelected = overlay.id in multiIds,
                            glyph = if (stickers) R.drawable.ic_add_reaction else R.drawable.ic_text,
                            labelColor = Ink0,
                            pxPerMs = pxPerMs,
                            rangeFor = ::range,
                            onSelect = { onSelect(overlay.id) },
                            onCommit = { drag -> range(drag).let { (start, end) -> onTiming(overlay.id, start, end) } },
                            snapMove = { move -> snap(overlay.id, overlay.startMs, overlay.endMs, move) },
                            decoration = { _, _ ->
                                if (overlay.id == selectedTextId) KeyframeMarks(overlay.keyframes.map { it.atMs }, pxPerMs)
                            },
                        )
                    }
                }
            }
        }
        if (stickers && lanes.isNotEmpty()) Spacer(Modifier.height(rowGap))
    }
}

/** What a sticker's bar says: its emoji, or the kind of picture. */
private class StickerLabels(val image: String, val gif: String, val drawing: String) {
    fun of(sticker: Sticker): String = when (sticker.kind) {
        StickerKind.EMOJI -> sticker.value
        StickerKind.IMAGE -> if (sticker.animated) gif else image
        StickerKind.DRAWING -> drawing
    }
}
