package com.savie.precut.ui.editor.captions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.model.text.CaptionStyle
import com.savie.precut.ui.components.TileFrame

/**
 * The caption style presets as 68 dp tiles (RD-CAP1), each with a small "Aa bb" drawn the way the
 * preset looks; the selected one gets the pink ring.
 */
@Composable
fun CaptionStyleTiles(selected: CaptionStyle?, onSelect: (CaptionStyle) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CaptionStyle.entries.forEach { style ->
            TileFrame(stringResource(style.label()), selected = selected == style, onClick = { onSelect(style) }) {
                StyleSample(style)
            }
        }
    }
}

@Composable
private fun StyleSample(style: CaptionStyle) {
    val first = stringResource(R.string.rd_media_caption_sample_first)
    val second = stringResource(R.string.rd_media_caption_sample_second)
    val outline = Shadow(Color.Black, blurRadius = 3f)
    val base = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    val text = when (style) {
        CaptionStyle.BOLD -> "$first $second".uppercase()
        else -> "$first $second"
    }
    when (style) {
        CaptionStyle.BOX -> Text(
            text,
            style = base,
            modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), SampleBox).padding(horizontal = 6.dp, vertical = 3.dp),
        )
        CaptionStyle.HIGHLIGHT, CaptionStyle.POP -> {
            val highlight = style.highlightArgb?.let { Color(it) } ?: Color.White
            Text(
                buildAnnotatedString {
                    append("$first ")
                    withStyle(SpanStyle(background = highlight, color = Color.Black)) { append(" $second ") }
                },
                style = base.copy(shadow = outline),
            )
        }
        CaptionStyle.MINIMAL -> Text(text, style = base.copy(fontWeight = FontWeight.Medium, shadow = Shadow(Color.Black, blurRadius = 8f)))
        CaptionStyle.CLASSIC, CaptionStyle.BOLD -> Text(text, style = base.copy(shadow = outline))
    }
}

private val SampleBox = RoundedCornerShape(6.dp)
