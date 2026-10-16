package com.savie.precut.model.export

import com.savie.precut.model.OutputSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSettingsTest {
    private val hd = OutputSize(1080, 1920)

    @Test
    fun bitrate_followsPixelsFrameRateAndPreset() {
        val recommended = ExportSettings().videoBitrate(hd)
        assertEquals((1080L * 1920 * 30 * 0.1f).toInt(), recommended)
        assertTrue(ExportSettings(bitrate = ExportBitrate.HIGHER).videoBitrate(hd) > recommended)
        assertTrue(ExportSettings(frameRate = 60).videoBitrate(hd) > recommended)
        // HEVC needs about two thirds for the same quality.
        assertTrue(ExportSettings(codec = ExportCodec.HEVC).videoBitrate(hd) < recommended)
    }

    @Test
    fun estimate_coversVideoAndAudioOverTheDuration() {
        val bytes = ExportSettings().estimatedBytes(hd, durationMs = 10_000L)
        val expected = (ExportSettings().videoBitrate(hd) + EXPORT_AUDIO_BITRATE) * 10.0 / 8
        assertEquals(expected, bytes.toDouble(), expected * 0.03)
        // Audio only is tiny.
        assertTrue(ExportSettings().estimatedBytes(hd, 10_000L, audioOnly = true) < 200_000L)
    }

    @Test
    fun fittedTo_stepsDownToWhatThePhoneEncodes() {
        val phone = ExportCapabilities(
            maxShortSide30 = mapOf(ExportCodec.H264 to 2160, ExportCodec.HEVC to 1080),
            maxShortSide60 = mapOf(ExportCodec.H264 to 1080),
        )
        val wanted = ExportSettings(resolution = ExportResolution.P4K, frameRate = 60, codec = ExportCodec.H264)
        assertEquals(ExportResolution.P1080, wanted.fittedTo(phone).resolution)
        assertEquals(60, wanted.fittedTo(phone).frameRate)
        assertEquals(ExportResolution.P4K, wanted.copy(frameRate = 30).fittedTo(phone).resolution)
        // HEVC 4K isn't there: 1080p HEVC.
        assertEquals(ExportResolution.P1080, wanted.copy(frameRate = 30, codec = ExportCodec.HEVC).fittedTo(phone).resolution)
        // No HEVC at all: H.264.
        assertEquals(ExportCodec.H264, ExportSettings(codec = ExportCodec.HEVC).fittedTo(ExportCapabilities.BASIC).codec)
        // HDR only when the phone can.
        assertFalse(ExportSettings(keepHdr = true).fittedTo(ExportCapabilities.BASIC).keepHdr)
    }

    @Test
    fun capabilities_compareShortSides() {
        val basic = ExportCapabilities.BASIC
        assertTrue(basic.supports(ExportResolution.P1080, 30, ExportCodec.H264))
        assertFalse(basic.supports(ExportResolution.P2K, 30, ExportCodec.H264))
        assertFalse(basic.supports(ExportCodec.HEVC))
    }
}
