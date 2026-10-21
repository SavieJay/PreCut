package com.savie.precut.ui.editor.preview

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.savie.precut.media.PlayerTextureView

/**
 * Black letterboxed area showing the composition preview. [textureView] (full screen) uses a
 * TextureView, which follows view scaling for pinch-zoom; [onSurfaceReady] runs when it gets the picture.
 */
@Composable
fun PreviewSurface(player: Player, modifier: Modifier = Modifier, textureView: Boolean = false, onSurfaceReady: () -> Unit = {}) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (textureView) VideoTexture(player, onSurfaceReady) else VideoFrame(player)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoFrame(player: Player) {
    // A SurfaceView is the cheap path, and the only video view CompositionPlayer takes directly.
    ContentFrame(player = player, modifier = Modifier.fillMaxSize(), surfaceType = SURFACE_TYPE_SURFACE_VIEW, keepContentOnReset = true)
}

/** Sized to the video like [ContentFrame] does; [PlayerTextureView] hands the player its surface. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoTexture(player: Player, onSurfaceReady: () -> Unit) {
    val presentation = rememberPresentationState(player, keepContentOnReset = true)
    val ready by rememberUpdatedState(onSurfaceReady)
    AndroidView(
        factory = { context -> PlayerTextureView(context).also { view -> view.onSurfaceReady = { ready() } } },
        update = { it.bind(player) },
        onRelease = { it.unbind() },
        modifier = Modifier.fillMaxSize().resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp),
    )
}
