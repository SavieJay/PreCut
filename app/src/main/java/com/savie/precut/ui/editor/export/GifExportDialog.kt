package com.savie.precut.ui.editor.export

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PillTab
import com.savie.precut.ui.components.PillTabs
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.GlassSolid
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
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
        containerColor = GlassSolid,
        shape = Radius.sheet,
        title = { Text(stringResource(R.string.export_gif_title), style = PreCutType.panelTitle, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Option(R.string.export_gif_size) {
                    val sizes = GifSize.entries
                    PillTabs(
                        sizes.map { PillTab(stringResource(if (it == GifSize.SMALL) R.string.export_gif_small else R.string.export_gif_medium, it.shortSidePx)) },
                        sizes.indexOf(gif.size),
                        { onChange(gif.copy(size = sizes[it])) },
                        Modifier.fillMaxWidth(),
                        fillWidth = true,
                    )
                }
                Option(R.string.export_settings_frame_rate) {
                    PillTabs(
                        GIF_FRAME_RATES.map { PillTab(it.toString()) },
                        GIF_FRAME_RATES.indexOf(gif.frameRate),
                        { onChange(gif.copy(frameRate = GIF_FRAME_RATES[it])) },
                        Modifier.fillMaxWidth(),
                        fillWidth = true,
                    )
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
                    Text(stringResource(R.string.export_gif_long), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        },
        confirmButton = { FilledButton(stringResource(R.string.export_gif_action), onExport) },
        dismissButton = { AccentTextButton(stringResource(R.string.action_cancel), onDismiss, color = TextSecondary) },
    )
}

@Composable
private fun Option(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title).uppercase(), style = PreCutType.section, color = TextMuted)
        content()
    }
}
