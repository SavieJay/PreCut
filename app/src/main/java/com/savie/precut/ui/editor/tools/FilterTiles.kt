package com.savie.precut.ui.editor.tools

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.media.look.LookThumbnailer
import com.savie.precut.ui.theme.ClipBackground

private val TileShape = RoundedCornerShape(8.dp)
private val TileSize = 60.dp

/** A filter tile: the clip's frame graded by [filterId] (ungraded when null), with its name. */
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

/** A tile with an icon instead of a picture (e.g. "Import .cube"). */
@Composable
fun IconTile(label: String, icon: Int, onClick: () -> Unit) {
    TileFrame(label, selected = false, onClick) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun TileFrame(label: String, selected: Boolean, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(TileSize).clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(TileSize)
                .clip(TileShape)
                .background(ClipBackground)
                .border(2.dp, if (selected) accent else Color.Transparent, TileShape),
            content = content,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
