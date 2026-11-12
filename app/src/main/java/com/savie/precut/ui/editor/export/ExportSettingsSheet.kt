package com.savie.precut.ui.editor.export

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.OutputSize
import com.savie.precut.model.export.ExportCapabilities
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.export.estimatedBytes
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutSpinner
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.GlassSolid
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextPrimary

/** What the export sheet can do. */
interface ExportSheetActions {
    fun onSettings(settings: ExportSettings)
    fun onExportVideo()
    fun onAudioOnly()
    fun onGif()
    fun onSaveFrame()
    fun onDismiss()
}

/**
 * EX-01 (RD-EX01): the title, the live size estimate, segmented resolution / frame rate / bitrate /
 * codec groups, HDR and end card switches, then Audio only, GIF ([GifExportDialog]) and Save frame
 * as quiet pills above the one pink Export video pill. Choices the phone can't encode
 * are greyed with the reason beside the group's label.
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
        containerColor = GlassSolid,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (defaultsOnly) R.string.settings_default_export else R.string.export_title),
                style = PreCutType.panelTitle,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!defaultsOnly) {
                SizeEstimateCard(
                    size = Formatter.formatShortFileSize(context, settings.estimatedBytes(outputSize, durationMs)),
                    duration = formatDurationMs(durationMs),
                )
            }
            ExportSettingGroups(settings, capabilities, actions::onSettings)
        }
        if (!defaultsOnly) {
            OtherExports(savingFrame, actions)
            FilledButton(
                stringResource(R.string.export_video),
                actions::onExportVideo,
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                icon = R.drawable.ic_share,
                large = true,
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Audio only, GIF and Save frame: quiet pills beside the one pink Export video. */
@Composable
private fun OtherExports(savingFrame: Boolean, actions: ExportSheetActions) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TonalButton(stringResource(R.string.export_audio_only), actions::onAudioOnly, Modifier.weight(1f).height(44.dp), icon = R.drawable.ic_music_note)
        TonalButton(stringResource(R.string.export_gif), actions::onGif, Modifier.weight(1f).height(44.dp), icon = R.drawable.ic_gif)
        if (savingFrame) {
            Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) { PreCutSpinner(size = 24.dp) }
        } else {
            TonalButton(stringResource(R.string.export_save_frame), actions::onSaveFrame, Modifier.weight(1f).height(44.dp), icon = R.drawable.ic_add_photo_alternate)
        }
    }
}
