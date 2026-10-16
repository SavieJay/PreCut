package com.savie.precut.ui.editor.export

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.OutputSize
import com.savie.precut.model.export.EXPORT_FRAME_RATES
import com.savie.precut.model.export.ExportBitrate
import com.savie.precut.model.export.ExportCapabilities
import com.savie.precut.model.export.ExportCodec
import com.savie.precut.model.export.ExportResolution
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.export.estimatedBytes
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.SwitchRow
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.export.label
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.TextSecondary

/** What the export sheet can do. */
interface ExportSheetActions {
    fun onSettings(settings: ExportSettings)
    fun onExportVideo()
    fun onAudioOnly()
    fun onSaveFrame()
    fun onDismiss()
}

/**
 * EX-01: resolution, frame rate, bitrate, codec and HDR, the estimated size, then Export video,
 * Audio only and Save frame. Choices the phone can't encode are disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSettingsSheet(
    settings: ExportSettings,
    capabilities: ExportCapabilities,
    /** The frame at the chosen resolution, for the size estimate. */
    outputSize: OutputSize,
    durationMs: Long,
    savingFrame: Boolean,
    actions: ExportSheetActions,
    /** Settings' "Default export settings": only the choices, no estimate or export buttons. */
    defaultsOnly: Boolean = false,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = actions::onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink1,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(if (defaultsOnly) R.string.settings_default_export else R.string.export_title), style = MaterialTheme.typography.titleMedium)
            Section(R.string.export_settings_resolution) {
                ExportResolution.entries.forEach { res ->
                    PreCutChip(
                        stringResource(res.label()), selected = settings.resolution == res,
                        onClick = { actions.onSettings(settings.copy(resolution = res)) },
                        enabled = capabilities.supports(res, settings.frameRate, settings.codec),
                    )
                }
            }
            ExportResolution.entries.lastOrNull { !capabilities.supports(it, settings.frameRate, settings.codec) }?.let { unsupported ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.export_unsupported, stringResource(unsupported.label())), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Section(R.string.export_settings_frame_rate) {
                EXPORT_FRAME_RATES.forEach { fps ->
                    PreCutChip(
                        fps.toString(), selected = settings.frameRate == fps,
                        onClick = { actions.onSettings(settings.copy(frameRate = fps)) },
                        enabled = capabilities.supports(settings.resolution, fps, settings.codec),
                    )
                }
            }
            Section(R.string.export_settings_bitrate) {
                ExportBitrate.entries.forEach { rate ->
                    PreCutChip(stringResource(rate.label()), selected = settings.bitrate == rate, onClick = { actions.onSettings(settings.copy(bitrate = rate)) })
                }
            }
            Section(R.string.export_settings_codec) {
                ExportCodec.entries.forEach { codec ->
                    PreCutChip(
                        stringResource(codec.label()), selected = settings.codec == codec,
                        onClick = { actions.onSettings(settings.copy(codec = codec)) }, enabled = capabilities.supports(codec),
                    )
                }
            }
            if (capabilities.hdr) {
                SwitchRow(label = stringResource(R.string.export_hdr), checked = settings.keepHdr, onCheckedChange = { actions.onSettings(settings.copy(keepHdr = it)) })
                Text(stringResource(R.string.export_hdr_note), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (defaultsOnly) return@Column
            Text(
                stringResource(
                    R.string.export_estimated,
                    Formatter.formatShortFileSize(context, settings.estimatedBytes(outputSize, durationMs)),
                    formatDurationMs(durationMs),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            FilledButton(stringResource(R.string.export_video), actions::onExportVideo, Modifier.fillMaxWidth(), icon = R.drawable.ic_movie, large = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TonalButton(stringResource(R.string.export_audio_only), actions::onAudioOnly, Modifier.weight(1f).height(44.dp), icon = R.drawable.ic_music_note)
                if (savingFrame) {
                    Row(Modifier.weight(1f).height(44.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Accent, strokeWidth = 3.dp)
                    }
                } else {
                    TonalButton(stringResource(R.string.export_save_frame), actions::onSaveFrame, Modifier.weight(1f).height(44.dp), icon = R.drawable.ic_image)
                }
            }
        }
    }
}

@Composable
private fun Section(title: Int, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}
