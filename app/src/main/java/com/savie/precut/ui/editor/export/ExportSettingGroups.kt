package com.savie.precut.ui.editor.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.model.export.EXPORT_FRAME_RATES
import com.savie.precut.model.export.ExportBitrate
import com.savie.precut.model.export.ExportCapabilities
import com.savie.precut.model.export.ExportCodec
import com.savie.precut.model.export.ExportResolution
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.ui.components.PillTab
import com.savie.precut.ui.components.PillTabs
import com.savie.precut.ui.components.SwitchRow
import com.savie.precut.ui.export.label
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glass
import com.savie.precut.ui.theme.glassRow

/** RD-EX01's estimate card: a caps label, the size large in tabular figures, the duration below. */
@Composable
internal fun SizeEstimateCard(size: String, duration: String) {
    Column(Modifier.fillMaxWidth().glass(Radius.card).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.rd_media_export_estimated_label).uppercase(), style = PreCutType.section, color = TextMuted)
        Text(size, style = PreCutType.timecode.copy(fontSize = 28.sp, lineHeight = 34.sp), color = TextPrimary)
        Text(duration, style = PreCutType.timecode, color = TextMuted)
    }
}

/** The segmented groups and switch rows, shared by the editor's sheet and Settings' defaults. */
@Composable
internal fun ExportSettingGroups(settings: ExportSettings, capabilities: ExportCapabilities, onSettings: (ExportSettings) -> Unit) {
    val unsupported = ExportResolution.entries.lastOrNull { !capabilities.supports(it, settings.frameRate, settings.codec) }
    Group(R.string.export_settings_resolution, note = unsupported?.let { stringResource(R.string.export_unsupported, stringResource(it.label())) }) {
        val all = ExportResolution.entries
        Segments(all.map { PillTab(stringResource(it.label()), enabled = capabilities.supports(it, settings.frameRate, settings.codec)) }, all.indexOf(settings.resolution)) {
            onSettings(settings.copy(resolution = all[it]))
        }
    }
    Group(R.string.export_settings_frame_rate) {
        Segments(EXPORT_FRAME_RATES.map { PillTab(it.toString(), enabled = capabilities.supports(settings.resolution, it, settings.codec)) }, EXPORT_FRAME_RATES.indexOf(settings.frameRate)) {
            onSettings(settings.copy(frameRate = EXPORT_FRAME_RATES[it]))
        }
    }
    Group(R.string.export_settings_bitrate) {
        val all = ExportBitrate.entries
        Segments(all.map { PillTab(stringResource(it.label())) }, all.indexOf(settings.bitrate)) { onSettings(settings.copy(bitrate = all[it])) }
    }
    Group(R.string.export_settings_codec) {
        val all = ExportCodec.entries
        Segments(all.map { PillTab(stringResource(it.label()), enabled = capabilities.supports(it)) }, all.indexOf(settings.codec)) {
            onSettings(settings.copy(codec = all[it]))
        }
    }
    Column(Modifier.fillMaxWidth().glassRow(Radius.card).padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (capabilities.hdr) {
            SwitchRow(
                label = stringResource(R.string.export_hdr),
                description = stringResource(R.string.export_hdr_note),
                checked = settings.keepHdr,
                onCheckedChange = { onSettings(settings.copy(keepHdr = it)) },
            )
        }
        // Off unless chosen (Phase 28): PreCut never marks a video on its own.
        SwitchRow(
            label = stringResource(R.string.export_end_card),
            description = stringResource(R.string.export_end_card_note),
            checked = settings.endCard,
            onCheckedChange = { onSettings(settings.copy(endCard = it)) },
        )
    }
}

@Composable
private fun Group(title: Int, note: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title).uppercase(), style = PreCutType.section, color = TextMuted)
            note?.let {
                Text(it, style = PreCutType.labelChip, color = TextMuted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
        content()
    }
}

@Composable
private fun Segments(tabs: List<PillTab>, selected: Int, onSelect: (Int) -> Unit) {
    PillTabs(tabs, selected, onSelect, Modifier.fillMaxWidth(), fillWidth = true)
}
