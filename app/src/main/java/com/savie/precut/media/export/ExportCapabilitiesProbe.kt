package com.savie.precut.media.export

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EncoderUtil
import com.savie.precut.model.export.ExportCapabilities
import com.savie.precut.model.export.ExportCodec
import com.savie.precut.model.export.ExportResolution
import javax.inject.Inject
import javax.inject.Singleton

/** Asks the phone's encoders what an export can be (EX-01's "Not supported on this device"). */
@Singleton
class ExportCapabilitiesProbe @Inject constructor() {

    /** Probed once; encoders don't change while the app runs. */
    val capabilities: ExportCapabilities by lazy(::probe)

    private fun probe(): ExportCapabilities {
        val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
        val max30 = HashMap<ExportCodec, Int>()
        val max60 = HashMap<ExportCodec, Int>()
        ExportCodec.entries.forEach { codec ->
            val mime = codec.mime()
            val video = encoders.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
                .mapNotNull { runCatching { it.getCapabilitiesForType(mime).videoCapabilities }.getOrNull() }
            max30[codec] = largest(video, 30)
            max60[codec] = largest(video, 60)
        }
        return ExportCapabilities(max30, max60, hdr = hdrSupported())
    }

    /** The largest 16:9 short side (portrait or landscape) any encoder takes at [fps]. */
    private fun largest(video: List<MediaCodecInfo.VideoCapabilities>, fps: Int): Int =
        ExportResolution.entries.reversed().firstOrNull { resolution ->
            val short = resolution.shortSidePx
            val long = (short * 16 + 8) / 9 / 2 * 2
            video.any { caps -> caps.areSizeAndRateSupported(long, short, fps.toDouble()) || caps.areSizeAndRateSupported(short, long, fps.toDouble()) }
        }?.shortSidePx ?: 0

    @OptIn(UnstableApi::class) // EncoderUtil
    private fun hdrSupported(): Boolean = runCatching {
        val hdr10 = ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .build()
        EncoderUtil.getSupportedEncodersForHdrEditing(MimeTypes.VIDEO_H265, hdr10).isNotEmpty()
    }.getOrDefault(false)

    private fun ExportCodec.mime() = when (this) {
        ExportCodec.H264 -> MimeTypes.VIDEO_H264
        ExportCodec.HEVC -> MimeTypes.VIDEO_H265
    }
}
