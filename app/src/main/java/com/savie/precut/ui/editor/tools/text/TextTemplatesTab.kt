package com.savie.precut.ui.editor.tools.text

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.savie.precut.model.OutputSize
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.TextTemplates
import com.savie.precut.ui.components.TileGrid
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink2

/** Templates (X-04e): one-tap looks, each tile drawn by the real renderer on its sample words. */
@Composable
fun TextTemplatesTab(overlay: TextOverlay, render: (TextOverlay, OutputSize) -> Bitmap?, onChange: (TextOverlay) -> Unit) {
    TileGrid(TextTemplates.all, columns = 3) { template ->
        val sample = remember(template) {
            render(template.applyTo(TextOverlay("t", template.sample, 0L, 1L)), TILE_FRAME)?.asImageBitmap()
        }
        val shape = RoundedCornerShape(8.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .height(56.dp)
                .clip(shape)
                .background(Ink2)
                .border(if (template.style.fontId == overlay.fontId && template.style.colorArgb == overlay.colorArgb) 2.dp else 0.dp, Accent, shape)
                .clickable { onChange(template.applyTo(overlay)) }
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            sample?.let { Image(it, contentDescription = template.sample, contentScale = ContentScale.Fit) }
        }
    }
}

/** Templates are drawn as if on a phone-width frame, so their proportions look right. */
private val TILE_FRAME = OutputSize(360, 640)
