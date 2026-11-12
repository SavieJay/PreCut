package com.savie.precut.ui.editor.tools

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.media.look.LookThumbnailer
import com.savie.precut.ui.components.PresetTile
import com.savie.precut.ui.components.TileFrame

/** A 68 dp filter tile (RD-T06): the clip's frame graded by [filterId] (ungraded when null), with its name. */
@Composable
fun FilterTile(
    label: String,
    filterId: String?,
    baseKey: ThumbnailKey?,
    loader: ThumbnailLoader,
    thumbnailer: LookThumbnailer,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val image by produceState<Bitmap?>(null, baseKey, filterId) {
        val base = baseKey?.let { loader.load(it) } ?: return@produceState
        value = if (filterId == null) base else thumbnailer.graded(base, filterId) ?: base
    }
    TileFrame(label, selected, onClick) {
        image?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

/** A tile with an icon instead of a picture (e.g. "None", "Import .cube"). */
@Composable
fun IconTile(label: String, icon: Int, onClick: () -> Unit, selected: Boolean = false) {
    PresetTile(label, icon, selected, onClick)
}
