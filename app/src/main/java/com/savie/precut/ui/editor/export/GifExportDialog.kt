package com.savie.precut.ui.editor.export

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.OutputSize
import com.savie.precut.model.export.GIF_FRAME_RATES
import com.savie.precut.model.export.GIF_LONG_MS
import com.savie.precut.model.export.GifSettings
import com.savie.precut.model.export.GifSize
import com.savie.precut.model.export.estimatedBytes
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.TextSecondary

/** GIF options from the export sheet: size and frame rate, the rough size, and a warning for long GIFs. */
@Composable
fun GifExportDialog(
    gif: GifSettings,
    /** The GIF's frame at the chosen size, for the estimate. */
    outputSize: OutputSize,
    durationMs: Long,
    onChange: (GifSettings) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        // Ink1, so the Ink2 chips stand out (as on the export sheet).
        containerColor = Ink1,
        title = { Text(stringResource(R.string.export_gif_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Option(R.string.export_gif_size) {
                    GifSize.entries.forEach { size ->
                        PreCutChip(
                            stringResource(if (size == GifSize.SMALL) R.string.export_gif_small else R.string.export_gif_medium, size.shortSidePx),
                            selected = gif.size == size,
                            onClick = { onChange(gif.copy(size = size)) },
                        )
                    }
                }
                Option(R.string.export_settings_frame_rate) {
                    GIF_FRAME_RATES.forEach { fps ->
                        PreCutChip(fps.toString(), selected = gif.frameRate == fps, onClick = { onChange(gif.copy(frameRate = fps)) })
                    }
                }
                Text(
                    stringResource(
                        R.string.export_estimated,
                        Formatter.formatShortFileSize(context, gif.estimatedBytes(outputSize, durationMs)),
                        formatDurationMs(durationMs),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                if (durationMs > GIF_LONG_MS) {
                    Text(stringResource(R.string.export_gif_long), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onExport) { Text(stringResource(R.string.export_gif_action), color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = TextSecondary) } },
    )
}

@Composable
private fun Option(title: Int, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}
