package com.savie.precut.ui.editor.crop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.ui.editor.CropDraft
import com.savie.precut.ui.theme.Ink1

/** Crop mode (T-12b): the picture with its crop rectangle, then the Ratio panel. Replaces the editor body. */
@Composable
fun CropMode(draft: CropDraft, frameKey: ThumbnailKey, loader: ThumbnailLoader, actions: CropActions, modifier: Modifier = Modifier) {
    Column(modifier) {
        CropPreview(
            frameKey = frameKey,
            loader = loader,
            mirrored = draft.clip.mirrored,
            rotationDegrees = draft.clip.rotationDegrees,
            crop = draft.crop,
            lockedAspect = draft.lockedAspect,
            onChange = actions::onChange,
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black).padding(16.dp).fillMaxSize(),
        )
        Column(Modifier.background(Ink1).navigationBarsPadding()) {
            CropPanel(draft.clip.cropRatio, actions)
        }
    }
}
