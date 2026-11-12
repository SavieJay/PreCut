package com.savie.precut.ui.editor

import com.savie.precut.ui.editor.timeline.TimelineFocus
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.model.clipById
import com.savie.precut.R
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.media.WaveformLoader
import com.savie.precut.ui.components.CaptureMenu
import com.savie.precut.ui.components.PreCutSpinner
import com.savie.precut.ui.editor.timeline.Timeline
import com.savie.precut.ui.editor.timeline.TimelineActions
import com.savie.precut.ui.editor.timeline.TimelineSelection
import com.savie.precut.ui.editor.timeline.timelineHeight
import com.savie.precut.ui.theme.Ink0

/**
 * Tools whose panels are tall (~400 dp): the timeline collapses to the main strip so the preview
 * keeps at least 40% of the height (DESIGN.md "Panel heights").
 */
val TALL_TOOLS = setOf(EditorTool.HSL, EditorTool.CURVES, EditorTool.TEXT, EditorTool.CAPTIONS) + TEXT_STYLE_TOOLS + STICKER_SHEET_TOOLS + EFFECT_TOOLS

/** The timeline with its pinned "+" add-media button (gallery or camera). */
@Composable
fun TimelineSection(
    state: EditorUiState.Ready,
    /** Read lazily: the playhead changes every frame. */
    positionMs: () -> Long,
    loader: ThumbnailLoader,
    waveforms: WaveformLoader,
    actions: TimelineActions,
    onAddMedia: () -> Unit,
    onCamera: (video: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The Speed panel grows with a curve graph.
    val curving = state.activeTool == EditorTool.SPEED && state.project.clipById(state.selectedClipId)?.speedCurve != null
    val focus = when {
        // A text's panels keep its bar visible and draggable (Phase 29E).
        state.activeTool in TEXT_PANEL_TOOLS || state.activeTool == EditorTool.CAPTIONS -> TimelineFocus.StripAndText
        state.activeTool in TALL_TOOLS || curving -> TimelineFocus.Strip
        else -> TimelineFocus.All
    }
    Box(modifier.fillMaxWidth().height(timelineHeight(state.project, focus))) {
        Timeline(
            project = state.project,
            selection = TimelineSelection(
                clipId = state.selectedClipId,
                textId = state.selectedTextId,
                audioId = state.selectedAudioId,
                layerId = state.selectedLayerId,
                missingIds = state.missingIds,
                multiIds = state.multiSelect.orEmpty(),
                multiSelectMode = state.multiSelect != null,
                transitionClipId = state.selectedTransitionId?.takeIf { state.activeTool == EditorTool.TRANSITION },
            ),
            positionMs = positionMs,
            loader = loader,
            waveforms = waveforms,
            actions = actions,
            focus = focus,
            addButton = { AddMediaButton(state.isImporting, onAddMedia, onCamera) },
        )
    }
}

/** The 48 dp white "+" (16 dp corners, RD `.addm`); opens the gallery/camera menu. New media goes in at the playhead. */
@Composable
private fun AddMediaButton(importing: Boolean, onAddMedia: () -> Unit, onCamera: (video: Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .clickable(enabled = !importing, role = Role.Button, onClickLabel = stringResource(R.string.timeline_add_media)) { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            if (importing) {
                PreCutSpinner(size = 22.dp)
            } else {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.timeline_add_media), tint = Ink0)
            }
        }
        CaptureMenu(expanded = menu, onDismiss = { menu = false }, onCamera = onCamera, onGallery = onAddMedia)
    }
}
