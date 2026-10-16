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
import com.savie.precut.model.effect.EffectLayer
import com.savie.precut.ui.editor.tools.effect.label
import com.savie.precut.ui.theme.EffectBar
import com.savie.precut.ui.theme.Ink0
import kotlin.math.roundToLong

/**
 * Effect layers as bars (S-03), stacked so overlaps get their own row; they move and trim like
 * look layers. Nothing is drawn when there are none.
 */
@Composable
fun EffectLayerRows(
    lanes: List<List<EffectLayer>>,
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
    Column {
        lanes.forEach { lane ->
            Box(Modifier.width(with(density) { widthPx.toDp() }).height(rowHeight)) {
                lane.forEach { layer ->
                    key(layer.id) {
                        fun range(drag: BarDrag) = TimelineGeometry.dragTextRange(
                            layer.startMs, layer.endMs, drag.moveMs, drag.startMs, drag.endMs, totalMs,
                        )
                        TimelineBar(
                            id = layer.id,
                            label = stringResource(layer.effect.type.label()),
                            color = EffectBar,
                            selected = layer.id == selectedLayerId,
                            glyph = R.drawable.ic_flare,
                            labelColor = Ink0,
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
