package com.savie.precut.ui.export

import androidx.annotation.StringRes
import com.savie.precut.R
import com.savie.precut.model.export.ExportBitrate
import com.savie.precut.model.export.ExportCodec
import com.savie.precut.model.export.ExportResolution

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
