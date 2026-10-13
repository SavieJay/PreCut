package com.savie.precut.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.media.WaveformLoader
import com.savie.precut.ui.components.CaptureMenu
import com.savie.precut.ui.editor.timeline.Timeline
import com.savie.precut.ui.editor.timeline.TimelineActions
import com.savie.precut.ui.editor.timeline.timelineHeight
import com.savie.precut.ui.editor.timeline.TimelineSelection
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextPrimary

/** The timeline plus the fixed "add media" button (gallery or camera) at its right edge. */
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
    var addMenu by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().height(timelineHeight(state.project))) {
        Timeline(
            project = state.project,
            selection = TimelineSelection(
                clipId = state.selectedClipId,
                textId = state.selectedTextId,
                audioId = state.selectedAudioId,
                layerId = state.selectedLayerId,
                missingIds = state.missingIds,
                multiIds = state.multiSelect.orEmpty(),
            ),
            positionMs = positionMs,
            loader = loader,
            waveforms = waveforms,
            actions = actions,
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 40.dp),
        ) {
            FilledIconButton(
                onClick = { addMenu = true },
                enabled = !state.isImporting,
                shape = RoundedCornerShape(8.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = TextPrimary, contentColor = Ink0),
                modifier = Modifier.size(40.dp),
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Ink0, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.timeline_add_media))
                }
            }
            // CapCut's "+": gallery or camera; new media goes in at the playhead.
            CaptureMenu(expanded = addMenu, onDismiss = { addMenu = false }, onCamera = onCamera, onGallery = onAddMedia)
        }
    }
}
