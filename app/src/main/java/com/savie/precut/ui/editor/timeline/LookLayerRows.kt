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
import com.savie.precut.model.look.LookLayer
import com.savie.precut.ui.editor.tools.filterNameRes
import com.savie.precut.ui.theme.LookLayerBar
import kotlin.math.roundToLong

/** Callbacks from the filter/adjust layer rows. */
interface LookLayerActions {
    fun onSelectLayer(layerId: String)
    fun onLayerTiming(layerId: String, startMs: Long, endMs: Long)
}

/**
 * Filter/adjust layers as bars (CapCut's filter track), stacked so overlaps get their own row.
 * Moves and trims like text bars. Nothing is drawn when there are no layers.
 */
@Composable
fun LookLayerRows(
    lanes: List<List<LookLayer>>,
    selectedLayerId: String?,
    pxPerMs: Float,
    widthPx: Float,
    rowHeight: Dp,
    rowGap: Dp,
    snap: (ownerId: String, startMs: Long, endMs: Long, moveMs: Long) -> Long,
    actions: LookLayerActions,
) {
    val density = LocalDensity.current
    val totalMs = (widthPx / pxPerMs).roundToLong()
    val adjustLabel = stringResource(R.string.layer_adjust)
    val filterLabel = stringResource(R.string.layer_filter)
    Column {
        lanes.forEach { lane ->
            Box(Modifier.width(with(density) { widthPx.toDp() }).height(rowHeight)) {
                lane.forEach { layer ->
                    key(layer.id) {
                        fun range(drag: BarDrag) = TimelineGeometry.dragTextRange(
                            layer.startMs, layer.endMs, drag.moveMs, drag.startMs, drag.endMs, totalMs,
                        )
                        val filterName = layer.look.filterId?.let(::filterNameRes)?.let { stringResource(it) }
                        TimelineBar(
                            id = layer.id,
                            label = filterName ?: if (layer.look.filterId != null) filterLabel else adjustLabel,
                            color = LookLayerBar,
                            selected = layer.id == selectedLayerId,
                            glyph = if (layer.look.filterId != null) R.drawable.ic_filter else R.drawable.ic_adjust,
                            pxPerMs = pxPerMs,
                            rangeFor = ::range,
                            onSelect = { actions.onSelectLayer(layer.id) },
                            onCommit = { drag -> range(drag).let { (start, end) -> actions.onLayerTiming(layer.id, start, end) } },
                            snapMove = { move -> snap(layer.id, layer.startMs, layer.endMs, move) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(rowGap))
        }
    }
}
