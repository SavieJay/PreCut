package com.savie.precut.ui.editor.tools.sticker

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.savie.precut.R
import com.savie.precut.media.sticker.assetUri
import com.savie.precut.model.sticker.PackSticker
import com.savie.precut.model.sticker.StickerPack
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.glassRow

/**
 * A sticker pack that ships with the app (Phase 19), 5 to a row, then the credit its licence
 * asks for. Tiles show an animated sticker's first frame; it plays once placed.
 */
@Composable
fun PackTab(pack: StickerPack, onPick: (StickerPack, PackSticker) -> Unit) {
    val shape = Radius.tile
    LazyVerticalGrid(
        GridCells.Fixed(5),
        Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(pack.stickers, key = { it.name }) { item ->
            AsyncImage(
                model = pack.assetUri(item),
                contentDescription = item.emoji,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .aspectRatio(1f)
                    .glassRow(shape)
                    .clickable(role = Role.Button) { onPick(pack, item) }
                    .padding(6.dp),
            )
        }
    }
    Text(stringResource(pack.credit()), style = PreCutType.labelChip, color = TextMuted)
}

@StringRes
private fun StickerPack.credit(): Int = when (this) {
    StickerPack.ANIMATED -> R.string.sticker_credit_noto_animated
    StickerPack.THREE_D -> R.string.sticker_credit_fluent_3d
}
