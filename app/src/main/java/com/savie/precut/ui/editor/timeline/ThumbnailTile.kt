package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.ui.theme.ClipBackground

/** One timeline frame. Loading is cancelled automatically when the tile leaves composition. */
@Composable
fun ThumbnailTile(loader: ThumbnailLoader, key: ThumbnailKey, modifier: Modifier = Modifier) {
    val bitmap by produceState(initialValue = loader.cached(key), key) {
        if (value == null) value = loader.load(key)
    }
    val image = bitmap
    if (image == null) {
        Box(modifier.background(ClipBackground))
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
