package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.components.ColorDot
import com.savie.precut.ui.components.formatHexColor

/** The design's text swatches (X-01): white, black, yellow, pink, orange, mint, blue, violet. */
private val SWATCHES = listOf(
    Color.White, Color.Black, Color(0xFFFFD43B), Color(0xFFFF4D6D),
    Color(0xFFFF8A3D), Color(0xFF38D9A9), Color(0xFF4DA3FF), Color(0xFF9B7BFF),
)

/** A custom-colour swatch (opens the picker) and 8 preset swatches, spread across the panel. */
@Composable
fun TextColorRow(overlay: TextOverlay, recentColors: List<Int>, onChange: (TextOverlay) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    fun withColor(argb: Int) = overlay.copy(colorArgb = argb, backgroundArgb = overlay.backgroundArgb?.let { backgroundFor(argb) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val custom = SWATCHES.none { it.toArgb() == overlay.colorArgb }
        ColorDot(
            color = Color(overlay.colorArgb),
            selected = custom,
            description = stringResource(R.string.color_custom),
            onClick = { picking = true },
            brush = Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.9f, 1f) }),
        )
        SWATCHES.forEach { color ->
            val argb = color.toArgb()
            ColorDot(color, selected = argb == overlay.colorArgb, description = formatHexColor(argb), onClick = { onChange(withColor(argb)) })
        }
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
fun backgroundFor(textArgb: Int): Int =
    (if (Color(textArgb).luminance() > 0.5f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.85f)).toArgb()
