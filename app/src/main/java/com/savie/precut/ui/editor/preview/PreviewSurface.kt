package com.savie.precut.ui.editor.preview

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW

/** Black letterboxed area showing the composition preview. */
@Composable
fun PreviewSurface(player: Player, modifier: Modifier = Modifier, textureView: Boolean = false) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        VideoFrame(player, textureView)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoFrame(player: Player, textureView: Boolean) {
    // A TextureView scales with its layout (pinch-zoom); a SurfaceView is cheaper otherwise.
    ContentFrame(
        player = player,
        modifier = Modifier.fillMaxSize(),
        surfaceType = if (textureView) SURFACE_TYPE_TEXTURE_VIEW else SURFACE_TYPE_SURFACE_VIEW,
        keepContentOnReset = true,
    )
}
