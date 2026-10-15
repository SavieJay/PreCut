package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.DEFAULT_IMAGE_DURATION_MS
import com.savie.precut.model.edit.MIN_CLIP_DURATION_MS
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.editor.timeline.ThumbnailTile
import com.savie.precut.ui.editor.timeline.TrimHandle
import com.savie.precut.ui.editor.timeline.TrimSide
import com.savie.precut.ui.theme.ClipBackground
import com.savie.precut.ui.theme.ClipSelection
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * T-01: the clip's whole source as a filmstrip; the kept range is ringed in white with handles
 * outside it, the rest dimmed. The title shows the length live; the trim applies on release.
 */
@Composable
fun TrimTool(
    clip: Clip,
    loader: ThumbnailLoader,
    onTrim: (trimStartMs: Long, trimEndMs: Long) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var range by remember(clip.id, clip.trimStartMs, clip.trimEndMs) { mutableStateOf(clip.trimStartMs to clip.trimEndMs) }
    val full = if (clip.type == ClipType.IMAGE) 0L to DEFAULT_IMAGE_DURATION_MS else 0L to clip.sourceDurationMs
    ToolPanel(
        title = stringResource(R.string.trim_duration, formatSeconds(range.second - range.first)),
        onDone = onDone,
        modifier = modifier,
        actions = {
            AccentTextButton(stringResource(R.string.adjust_reset), { onTrim(full.first, full.second) }, enabled = range != full, color = TextSecondary)
        },
    ) {
        Filmstrip(clip, range, loader, onDrag = { range = it }, onCommit = { onTrim(range.first, range.second) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.trim_in, formatPreciseMs(range.first)), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(stringResource(R.string.trim_source, formatSeconds(clip.sourceDurationMs)), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(stringResource(R.string.trim_out, formatPreciseMs(range.second)), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun Filmstrip(
    clip: Clip,
    range: Pair<Long, Long>,
    loader: ThumbnailLoader,
    onDrag: (Pair<Long, Long>) -> Unit,
    onCommit: () -> Unit,
) {
    val density = LocalDensity.current
    val currentRange by rememberUpdatedState(range)
    BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
        val widthPx = constraints.maxWidth.toFloat()
        val msPerPx = clip.sourceDurationMs.coerceAtLeast(1L) / widthPx
        val heightPx = with(density) { 56.dp.roundToPx() }
        val tilePx = heightPx * 0.75f
        val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(clip.sourceDurationMs)
        fun px(ms: Long) = ms / msPerPx
        val startPx = px(range.first)
        val endPx = px(range.second)
        // Thumbnails across the whole source.
        Box(Modifier.offset(y = 4.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(6.dp)).background(ClipBackground)) {
            val tiles = (widthPx / tilePx).toInt() + 1
            repeat(tiles) { i ->
                val sourceMs = if (clip.type == ClipType.IMAGE) 0L else ((i + 0.5f) * tilePx * msPerPx).roundToLong().coerceAtMost(clip.sourceDurationMs)
                ThumbnailTile(
                    loader = loader,
                    key = ThumbnailKey(clip.uri, clip.type, sourceMs, heightPx, clip.sourceRotationDegrees),
                    modifier = Modifier.offset { IntOffset((i * tilePx).roundToInt(), 0) }.width(with(density) { tilePx.toDp() }).height(56.dp),
                )
            }
            Box(Modifier.width(with(density) { startPx.toDp() }).height(56.dp).background(Ink0.copy(alpha = 0.72f)))
            Box(Modifier.offset { IntOffset(endPx.roundToInt(), 0) }.width(with(density) { (widthPx - endPx).coerceAtLeast(0f).toDp() }).height(56.dp).background(Ink0.copy(alpha = 0.72f)))
        }
        // The kept range: a white ring with handles outside it.
        Box(
            Modifier
                .offset { IntOffset(startPx.roundToInt(), with(density) { 2.dp.roundToPx() }) }
                .size(with(density) { (endPx - startPx).coerceAtLeast(0f).toDp() }, 60.dp)
                .border(2.dp, ClipSelection, RoundedCornerShape(7.dp)),
        )
        fun drag(isStart: Boolean, amountPx: Float) {
            val (s, e) = currentRange
            val delta = (amountPx * msPerPx).roundToLong()
            onDrag(
                if (isStart) (s + delta).coerceIn(0L, e - minLength) to e
                else s to (e + delta).coerceIn(s + minLength, clip.sourceDurationMs),
            )
        }
        TrimHandle(
            TrimSide.START,
            onDrag = { drag(isStart = true, it) },
            onDragEnd = onCommit,
            modifier = Modifier
                .offset { IntOffset(startPx.roundToInt() - with(density) { 14.dp.roundToPx() }, with(density) { 2.dp.roundToPx() }) }
                .height(60.dp),
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp),
        )
        TrimHandle(
            TrimSide.END,
            onDrag = { drag(isStart = false, it) },
            onDragEnd = onCommit,
            modifier = Modifier
                .offset { IntOffset(endPx.roundToInt(), with(density) { 2.dp.roundToPx() }) }
                .height(60.dp),
            shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp),
        )
    }
}
