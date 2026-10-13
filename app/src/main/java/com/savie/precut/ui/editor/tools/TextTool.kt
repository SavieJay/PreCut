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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.TextOverlay

private val SWATCHES = listOf(
    Color.White, Color.Black, Color(0xFFFF4D6D), Color(0xFFFFD43B),
    Color(0xFF38D9A9), Color(0xFF4DABF7), Color(0xFFFF922B), Color(0xFFB197FC),
)

/** Edits the selected text overlay; every change is applied immediately. */
@Composable
fun TextTool(
    overlay: TextOverlay,
    onChange: (TextOverlay) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember(overlay.id, overlay.textSizeSp) { mutableFloatStateOf(overlay.textSizeSp) }
    ToolPanel(stringResource(R.string.text_title), onDone, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = overlay.text,
                onValueChange = { onChange(overlay.copy(text = it)) },
                placeholder = { Text(stringResource(R.string.text_placeholder)) },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.text_delete))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.text_size), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
            Slider(
                value = size,
                onValueChange = { size = it },
                onValueChangeFinished = { onChange(overlay.copy(textSizeSp = size)) },
                valueRange = 14f..80f,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SWATCHES.forEach { color ->
                    val argb = color.toArgb()
                    Swatch(color, selected = argb == overlay.colorArgb) {
                        onChange(overlay.copy(colorArgb = argb, backgroundArgb = overlay.backgroundArgb?.let { backgroundFor(color) }))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.text_background), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = overlay.backgroundArgb != null,
                onCheckedChange = { on ->
                    onChange(overlay.copy(backgroundArgb = if (on) backgroundFor(Color(overlay.colorArgb)) else null))
                },
            )
        }
    }
}

/** A translucent backdrop that contrasts with the text colour. */
private fun backgroundFor(textColor: Color): Int =
    (if (textColor.luminance() > 0.5f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.85f)).toArgb()

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
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
