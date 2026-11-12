package com.savie.precut.ui.editor.tools.text

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.OutputSize
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.TextTemplate
import com.savie.precut.model.text.TextTemplateCategory
import com.savie.precut.model.text.TextTemplates
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.TileGrid
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.glassRow

/**
 * Templates (X-04e): one-tap looks in groups, each tile drawn by the real renderer on its sample
 * words. Opens on the group of the look the text already has.
 */
@Composable
fun TextTemplatesTab(overlay: TextOverlay, render: (TextOverlay, OutputSize) -> Bitmap?, onChange: (TextOverlay) -> Unit) {
    var category by rememberSaveable {
        mutableStateOf(TextTemplates.all.firstOrNull { it.matches(overlay) }?.category ?: TextTemplateCategory.TITLE)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextTemplateCategory.entries.forEach { c ->
            PreCutChip(stringResource(c.label()), selected = category == c, onClick = { category = c })
        }
    }
    TileGrid(TextTemplates.inCategory(category), columns = 3) { template ->
        val sample = remember(template) {
            render(template.applyTo(TextOverlay("t", template.sample, 0L, 1L)), TILE_FRAME)?.asImageBitmap()
        }
        val shape = Radius.tile
        val on = template.matches(overlay)
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .glassRow(shape)
                .then(if (on) Modifier.border(3.dp, Accent, shape) else Modifier)
                .selectable(on, role = Role.RadioButton) { onChange(template.applyTo(overlay)) }
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            sample?.let { Image(it, contentDescription = template.sample, contentScale = ContentScale.Fit) }
        }
    }
}

/** Whether [overlay] already has this template's look (as far as the tile's ring is concerned). */
private fun TextTemplate.matches(overlay: TextOverlay) = style.fontId == overlay.fontId && style.colorArgb == overlay.colorArgb

@StringRes
private fun TextTemplateCategory.label(): Int = when (this) {
    TextTemplateCategory.TITLE -> R.string.text_templates_title
    TextTemplateCategory.SOCIAL -> R.string.text_templates_social
    TextTemplateCategory.CAPTION -> R.string.text_templates_caption
    TextTemplateCategory.SCRIPT -> R.string.text_templates_script
    TextTemplateCategory.FUN -> R.string.text_templates_fun
}

/** Templates are drawn as if on a phone-width frame, so their proportions look right. */
private val TILE_FRAME = OutputSize(360, 640)
