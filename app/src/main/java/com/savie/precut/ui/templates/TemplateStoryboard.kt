package com.savie.precut.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.savie.precut.model.clipById
import com.savie.precut.model.template.ProjectTemplate
import com.savie.precut.model.text.BundledFont
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.ui.editor.tools.text.resource
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.LookLayerBar
import com.savie.precut.ui.theme.MusicBar
import com.savie.precut.ui.theme.OverlayBar
import com.savie.precut.ui.theme.StickerBar

/**
 * A template drawn from its own data, for templates without a rendered preview (the built-in
 * ones ship no footage): its first text in its own font and colours, over a bar of its slots
 * sized by their lengths.
 */
@Composable
fun TemplateStoryboard(template: ProjectTemplate, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val title = template.project.textOverlays.firstOrNull { it.sticker == null }
    val lengths = remember(template) { template.slots.map { template.project.clipById(it.clipId)?.timelineDurationMs() ?: 0L } }
    val family = remember(title?.fontId) { BundledFont.entries.firstOrNull { it.id == title?.fontId }?.let { FontFamily(Font(it.resource())) } }
    Box(modifier.background(Brush.verticalGradient(listOf(Ink3, Ink1)))) {
        title?.let {
            Text(
                it.text,
                color = Color(it.colorArgb),
                fontFamily = family,
                fontWeight = if (it.bold || family == null) FontWeight.Bold else null,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp).height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            lengths.forEachIndexed { index, length ->
                Box(Modifier.weight(length.coerceAtLeast(1L).toFloat()).fillMaxHeight().background(SLOT_COLOURS[index % SLOT_COLOURS.size], RoundedCornerShape(2.dp)))
            }
        }
    }
}

private val SLOT_COLOURS = listOf(OverlayBar, StickerBar, MusicBar, LookLayerBar)
