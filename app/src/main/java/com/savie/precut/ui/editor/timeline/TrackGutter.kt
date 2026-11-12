package com.savie.precut.ui.editor.timeline

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.AudioKind
import com.savie.precut.model.Project
import com.savie.precut.model.audioLanes
import com.savie.precut.model.effectLanes
import com.savie.precut.model.lookLayerLanes
import com.savie.precut.model.overlayLanes
import com.savie.precut.model.stickerLanes
import com.savie.precut.model.textLanes
import com.savie.precut.ui.theme.EffectBar
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.LookLayerBar
import com.savie.precut.ui.theme.MusicBar
import com.savie.precut.ui.theme.OverlayBar
import com.savie.precut.ui.theme.RowFill
import com.savie.precut.ui.theme.RowLine
import com.savie.precut.ui.theme.StickerBar
import com.savie.precut.ui.theme.TextLaneBar
import com.savie.precut.ui.theme.TrackLabel
import com.savie.precut.ui.theme.VoiceoverBar

/** Width of the track gutter pinned at the timeline's left (RD-E03 redline 5). */
internal val GutterWidth = 44.dp

/** A lane's gutter tile: its colour and glyph, and where the lane sits from the timeline's top. */
internal data class GutterLane(val color: Color, @DrawableRes val icon: Int, val top: Dp, val height: Dp, val video: Boolean = false)

/** The lanes in [TimelineLanes]' order, at the same heights and gaps as [timelineHeight] adds up. */
internal fun gutterLanes(project: Project, focus: TimelineFocus): List<GutterLane> = buildList {
    val all = focus == TimelineFocus.All
    var y = TopPadding + RulerHeight + StripGap
    if (all) {
        repeat(project.overlayLanes().size) {
            add(GutterLane(OverlayBar, R.drawable.ic_overlay, y, OverlayRowHeight))
            y += OverlayRowHeight + StripGap
        }
    }
    add(GutterLane(Color.Unspecified, R.drawable.ic_movie, y, ClipHeight, video = true))
    if (focus == TimelineFocus.Strip) return@buildList
    y += ClipHeight + StripGap
    fun rows(count: Int, color: Color, @DrawableRes icon: Int) = repeat(count) {
        add(GutterLane(color, icon, y, TextLaneHeight))
        y += TextLaneHeight + RowGap
    }
    if (all) rows(project.lookLayerLanes().size, LookLayerBar, R.drawable.ic_filter)
    rows(project.stickerLanes().size, StickerBar, R.drawable.ic_add_reaction)
    if (all) rows(project.effectLanes().size, EffectBar, R.drawable.ic_flare)
    repeat(project.textLanes().size.coerceAtLeast(1)) { index ->
        if (index > 0) y += RowGap
        add(GutterLane(TextLaneBar, R.drawable.ic_title, y, TextLaneHeight))
        y += TextLaneHeight
    }
    if (!all) return@buildList
    val audio = project.audioLanes().ifEmpty { listOf(emptyList()) }
    audio.forEach { lane ->
        y += RowGap
        val voice = lane.isNotEmpty() && lane.all { it.kind == AudioKind.VOICEOVER }
        add(GutterLane(if (voice) VoiceoverBar else MusicBar, if (voice) R.drawable.ic_mic else R.drawable.ic_music_note, y, AudioRowHeight))
        y += AudioRowHeight
    }
}

/**
 * The 44 dp gutter: a 28 dp tile in each lane's colour with a dark glyph, over a fade from Base 0
 * so bars slide underneath. The main strip's tile is [videoTile] (the clip-sound switch). Tiles
 * have no touch handling of their own, so taps under them still reach the timeline.
 */
@Composable
internal fun TrackGutter(lanes: List<GutterLane>, modifier: Modifier = Modifier, videoTile: @Composable () -> Unit) {
    Box(modifier.width(GutterWidth).fillMaxHeight()) {
        Box(
            Modifier
                .padding(top = TopPadding + RulerHeight)
                .fillMaxHeight()
                .width(GutterWidth)
                .background(Brush.horizontalGradient(0f to Ink0, 0.78f to Ink0, 1f to Ink0.copy(alpha = 0f))),
        )
        lanes.forEach { lane ->
            Box(
                Modifier.offset(y = lane.top).height(lane.height).width(GutterWidth).padding(start = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (lane.video) videoTile() else TrackTile(lane.color, lane.icon)
            }
        }
    }
}

internal val TrackTileShape = RoundedCornerShape(9.dp)

/** A 28 dp track tile (RD `.ti`); [outlined] is the quiet white-6% look (`.ti.mute`). */
@Composable
internal fun TrackTile(color: Color, @DrawableRes icon: Int, modifier: Modifier = Modifier, outlined: Boolean = false, iconColor: Color = TrackLabel) {
    Box(
        modifier
            .size(28.dp)
            .background(if (outlined) RowFill else color, TrackTileShape)
            .then(if (outlined) Modifier.border(1.dp, RowLine, TrackTileShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
    }
}
