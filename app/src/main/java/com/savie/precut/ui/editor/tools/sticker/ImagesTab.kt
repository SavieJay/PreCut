package com.savie.precut.ui.editor.tools.sticker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.savie.precut.R
import com.savie.precut.media.sticker.LibraryImage
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Outline
import com.savie.precut.ui.theme.TextSecondary

private val TileShape = RoundedCornerShape(8.dp)

/** Import, then the pictures in My images, 4 to a row; long-press removes one (S-01c). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImagesTab(images: List<LibraryImage>, actions: StickerSheetActions) {
    val haptics = LocalHapticFeedback.current
    LazyVerticalGrid(
        GridCells.Fixed(4),
        Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "import") {
            Column(
                Modifier
                    .aspectRatio(1f)
                    .clip(TileShape)
                    .border(1.dp, Outline, TileShape)
                    .combinedClickable(role = Role.Button, onClick = actions::onImport),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(R.drawable.ic_add_photo_alternate), contentDescription = null, tint = Accent)
                Text(stringResource(R.string.sticker_import), style = MaterialTheme.typography.labelMedium, color = Accent)
            }
        }
        items(images, key = { it.file.name }) { image ->
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(TileShape)
                    .background(Ink2)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { actions.onImage(image) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            actions.onRemoveImage(image)
                        },
                    ),
            ) {
                AsyncImage(
                    model = image.file,
                    contentDescription = stringResource(if (image.animated) R.string.sticker_gif else R.string.sticker_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                )
            }
        }
    }
    Text(
        stringResource(if (images.isEmpty()) R.string.sticker_images_empty else R.string.sticker_images_note),
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
