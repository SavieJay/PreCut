package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.TextOverlay

private val SWATCHES = listOf(
    Color.White, Color.Black, Color(0xFFFF4D6D), Color(0xFFFFD43B),
    Color(0xFF38D9A9), Color(0xFF4DABF7), Color(0xFFFF922B), Color(0xFFB197FC),
)

/** Text colour (swatches plus a custom colour picker) and the background switch. */
@Composable
fun TextStyleRow(overlay: TextOverlay, recentColors: List<Int>, onChange: (TextOverlay) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    fun withColor(argb: Int) = overlay.copy(colorArgb = argb, backgroundArgb = overlay.backgroundArgb?.let { backgroundFor(Color(argb)) })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val custom = SWATCHES.none { it.toArgb() == overlay.colorArgb }
            CustomColorSwatch(selected = custom, onClick = { picking = true })
            SWATCHES.forEach { color ->
                val argb = color.toArgb()
                ColorSwatch(color, selected = argb == overlay.colorArgb) { onChange(withColor(argb)) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.text_background), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = overlay.backgroundArgb != null,
            onCheckedChange = { on -> onChange(overlay.copy(backgroundArgb = if (on) backgroundFor(Color(overlay.colorArgb)) else null)) },
        )
    }
    if (picking) {
        ColorPickerSheet(
            initialArgb = overlay.colorArgb,
            recentColors = recentColors,
            onPicked = { argb ->
                picking = false
                onChange(withColor(argb))
            },
            onDismiss = { picking = false },
        )
    }
}

/** A translucent backdrop that contrasts with the text colour. */
private fun backgroundFor(textColor: Color): Int =
    (if (textColor.luminance() > 0.5f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.85f)).toArgb()

@Composable
fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .clickable(onClick = onClick),
    )
}

/** Rainbow swatch that opens the full colour picker. */
@Composable
private fun CustomColorSwatch(selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val description = stringResource(R.string.color_custom)
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.9f, 1f) }))
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
    )
}
