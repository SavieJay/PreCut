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

/** Black letterboxed area showing the composition preview. */
@Composable
fun PreviewSurface(player: Player, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        VideoFrame(player)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoFrame(player: Player) {
    ContentFrame(player = player, modifier = Modifier.fillMaxSize(), keepContentOnReset = true)
}
