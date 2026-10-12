package com.savie.precut.ui.editor.preview

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import coil3.compose.AsyncImage
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType

/** Black letterboxed area showing the video, or a still image until Phase 3 previews images. */
@Composable
fun PreviewSurface(player: Player, clip: Clip?, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        when (clip?.type) {
            ClipType.VIDEO -> VideoFrame(player)
            ClipType.IMAGE -> AsyncImage(
                model = clip.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            null -> Unit
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoFrame(player: Player) {
    ContentFrame(player = player, modifier = Modifier.fillMaxSize())
}
