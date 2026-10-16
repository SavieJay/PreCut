package com.savie.precut.model.export

import com.savie.precut.model.OutputSize
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/** Output resolution by the frame's short side (EX-01). */
@Serializable
enum class ExportResolution(val shortSidePx: Int) { P480(480), P720(720), P1080(1080), P2K(1440), P4K(2160) }

/** Bitrate presets, in bits per pixel per frame (H.264; HEVC needs about two thirds). */
@Serializable
enum class ExportBitrate(val bitsPerPixel: Float) { LOWER(0.06f), RECOMMENDED(0.1f), HIGHER(0.16f) }

@Serializable
enum class ExportCodec { H264, HEVC }

/** What an export makes (EX-01); the defaults are CapCut's. */
@Serializable
data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.P1080,
    val frameRate: Int = 30,
    val bitrate: ExportBitrate = ExportBitrate.RECOMMENDED,
    val codec: ExportCodec = ExportCodec.H264,
    /** Keep HDR10/HLG sources as HDR (only offered when the phone can encode it). */
    val keepHdr: Boolean = false,
)

/** Frame rates offered; an export can't go above its sources (frames are only dropped). */
val EXPORT_FRAME_RATES = listOf(24, 25, 30, 50, 60)

const val EXPORT_AUDIO_BITRATE = 128_000

/** What this phone's encoders can do, per codec: the largest short side at 30 fps and at 60 fps. */
data class ExportCapabilities(
    val maxShortSide30: Map<ExportCodec, Int>,
    val maxShortSide60: Map<ExportCodec, Int>,
    val hdr: Boolean = false,
) {
    fun supports(codec: ExportCodec): Boolean = (maxShortSide30[codec] ?: 0) > 0

    fun supports(resolution: ExportResolution, frameRate: Int, codec: ExportCodec): Boolean =
        resolution.shortSidePx <= ((if (frameRate > 30) maxShortSide60 else maxShortSide30)[codec] ?: 0)

    companion object {
        /** Enough for tests and as a fallback when the phone can't be asked: H.264 1080p. */
        val BASIC = ExportCapabilities(mapOf(ExportCodec.H264 to 1080), mapOf(ExportCodec.H264 to 1080))
    }
}

/** Video bitrate for a frame of [size] at these settings. */
fun ExportSettings.videoBitrate(size: OutputSize): Int {
    val perPixel = bitrate.bitsPerPixel * if (codec == ExportCodec.HEVC) 0.65f else 1f
    return (size.width.toLong() * size.height * frameRate * perPixel).toInt().coerceIn(MIN_VIDEO_BITRATE, MAX_VIDEO_BITRATE)
}

/** About how big the file will be: video plus audio over [durationMs] (audio only when [audioOnly]). */
fun ExportSettings.estimatedBytes(size: OutputSize, durationMs: Long, audioOnly: Boolean = false): Long {
    val bitsPerSecond = (if (audioOnly) 0 else videoBitrate(size)).toLong() + EXPORT_AUDIO_BITRATE
    // A little for the container.
    return (bitsPerSecond * durationMs / 8_000.0 * 1.02).roundToLong()
}

/**
 * These settings moved to what the phone supports: HEVC falls back to H.264, then the resolution
 * steps down until it fits (60 fps drops to 30 when nothing fits).
 */
fun ExportSettings.fittedTo(capabilities: ExportCapabilities): ExportSettings {
    var fitted = if (capabilities.supports(codec)) this else copy(codec = ExportCodec.H264)
    if (!capabilities.hdr) fitted = fitted.copy(keepHdr = false)
    val sizes = ExportResolution.entries.filter { it.shortSidePx <= fitted.resolution.shortSidePx }.reversed()
    sizes.firstOrNull { capabilities.supports(it, fitted.frameRate, fitted.codec) }?.let { return fitted.copy(resolution = it) }
    val at30 = fitted.copy(frameRate = minOf(fitted.frameRate, 30))
    sizes.firstOrNull { capabilities.supports(it, at30.frameRate, at30.codec) }?.let { return at30.copy(resolution = it) }
    return at30.copy(resolution = ExportResolution.P480)
}

private const val MIN_VIDEO_BITRATE = 500_000
private const val MAX_VIDEO_BITRATE = 100_000_000
