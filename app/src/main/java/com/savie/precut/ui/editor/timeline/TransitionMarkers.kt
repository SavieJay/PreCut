package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.slotStartTimesMs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.savie.precut.R
import com.savie.precut.model.Project
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.motion.MotionMath
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.ui.editor.tools.tile
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TransitionMarker
import kotlin.math.roundToInt

private val MarkerSize = 24.dp

/**
 * A marker on each junction between clips (T-16): a diamond in an Ink0 square, or the transition's
 * icon once one is set; the selected one is Accent with a band over the transition's length.
 * Markers beside the selected clip step aside for its trim handles.
 */
@Composable
fun TransitionMarkers(project: Project, selection: TimelineSelection, pxPerMs: Float, onSelect: (String) -> Unit) {
    if (selection.multiSelectMode || project.clips.size < 2) return
    val density = LocalDensity.current
    // Junctions sit where each clip's own stretch begins (after any overlap).
    val starts = project.slotStartTimesMs()
    val clips = project.clips
    for (index in 0 until clips.lastIndex) {
        val clip = clips[index]
        if (clip.id == selection.clipId || clips[index + 1].id == selection.clipId) continue
        val junctionPx = TimelineGeometry.msToPx(starts[index + 1], pxPerMs)
        val selected = clip.id == selection.transitionClipId
        val transition = clip.transitionOut
        if (selected && transition != null) {
            val fitted = if (transition.type.overlapping) {
                MotionMath.fittedOverlapMs(transition, clip.timelineDurationMs(), clips[index + 1].timelineDurationMs())
            } else {
                MotionMath.fittedTransitionMs(transition, clip.timelineDurationMs(), clips[index + 1].timelineDurationMs())
            }
            val bandPx = TimelineGeometry.msToPx(fitted, pxPerMs)
            Box(
                Modifier
                    .offset { IntOffset((junctionPx - bandPx / 2).roundToInt(), 0) }
                    .width(with(density) { bandPx.toDp() })
                    .height(ClipHeight)
                    .zIndex(2f)
                    .background(Accent.copy(alpha = 0.28f))
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        drawLine(Accent, Offset(0f, 0f), Offset(0f, size.height), stroke)
                        drawLine(Accent, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
                    },
            )
        }
        val shape = RoundedCornerShape(6.dp)
        val contentColor = if (selected) Color.White else TransitionMarker
        Box(
            Modifier
                .offset { IntOffset((junctionPx - MarkerSize.toPx() / 2).roundToInt(), ((ClipHeight - MarkerSize) / 2).roundToPx()) }
                .size(MarkerSize)
                .zIndex(3f)
                .background(if (selected) Accent else Ink0, shape)
                .border(1.5.dp, if (selected) Color.White else TransitionMarker, shape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.transition_marker)) { onSelect(clip.id) },
            contentAlignment = Alignment.Center,
        ) {
            if (transition != null) {
                Icon(painterResource(transition.type.tile.first), stringResource(R.string.transition_marker), tint = contentColor, modifier = Modifier.size(14.dp))
            } else {
                Box(Modifier.size(7.dp).rotate(45f).background(contentColor))
            }
        }
    }
}
