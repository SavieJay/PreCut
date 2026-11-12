package com.savie.precut.ui.export

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.export.ExportKind
import com.savie.precut.model.export.ExportBitrate
import com.savie.precut.model.export.ExportCodec
import com.savie.precut.model.export.ExportResolution
import com.savie.precut.ui.components.formatDurationMs

@StringRes
fun ExportResolution.label(): Int = when (this) {
    ExportResolution.P480 -> R.string.export_res_p480
    ExportResolution.P720 -> R.string.export_res_p720
    ExportResolution.P1080 -> R.string.export_res_p1080
    ExportResolution.P2K -> R.string.export_res_p2k
    ExportResolution.P4K -> R.string.export_res_p4k
}

@StringRes
fun ExportBitrate.label(): Int = when (this) {
    ExportBitrate.LOWER -> R.string.export_bitrate_lower
    ExportBitrate.RECOMMENDED -> R.string.export_bitrate_recommended
    ExportBitrate.HIGHER -> R.string.export_bitrate_higher
}

@StringRes
fun ExportCodec.label(): Int = when (this) {
    ExportCodec.H264 -> R.string.export_codec_h264
    ExportCodec.HEVC -> R.string.export_codec_hevc
}

/** The progress screen's subtitle: resolution, frame rate and length (or the GIF's, or just the length for audio). */
@Composable
internal fun qualityOf(info: ExportInfo): String = when (info.kind) {
    ExportKind.AUDIO -> formatDurationMs(info.durationMs)
    ExportKind.GIF -> stringResource(R.string.export_quality_gif, info.settings.gif.size.shortSidePx, info.settings.gif.frameRate, formatDurationMs(info.durationMs))
    ExportKind.VIDEO -> stringResource(R.string.export_quality, stringResource(info.settings.resolution.label()), info.settings.frameRate, formatDurationMs(info.durationMs))
}
