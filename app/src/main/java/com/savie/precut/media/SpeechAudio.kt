package com.savie.precut.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.audio.PcmDecoder
import com.savie.precut.model.ai.SpeechPart
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject

/** The sound sent for captions (Phase 21A), once exported: how loud it is, and cutting it into parts. */
interface SpeechAudio {
    /** Peak levels (0..1) of [file], one per [windowMs]. Throws `IOException` when it can't be read. */
    suspend fun levels(file: File, windowMs: Long): FloatArray

    /** Copies [part] of [file] (an M4A) into [target], without re-encoding. Throws `IOException` on failure. */
    suspend fun cut(file: File, part: SpeechPart, target: File)
}

class AndroidSpeechAudio @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SpeechAudio {

    override suspend fun levels(file: File, windowMs: Long): FloatArray = withContext(io) {
        var peaks: PeakAccumulator? = null
        try {
            val found = PcmDecoder.decode(
                context,
                Uri.fromFile(file).toString(),
                onFormat = { rate, channels -> if (peaks == null) peaks = PeakAccumulator((rate * windowMs / 1_000).toInt().coerceAtLeast(1), channels) },
                onPcm = { samples, count, _ -> peaks?.add(samples, count) },
            )
            if (!found) throw IOException("No sound in $file")
        } catch (e: IllegalStateException) {
            // MediaCodec reports decoder failures this way.
            throw IOException("Couldn't read the sound", e)
        }
        peaks?.result() ?: FloatArray(0)
    }

    override suspend fun cut(file: File, part: SpeechPart, target: File) = withContext(io) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No sound in $file")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val out = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = out
            val outTrack = out.addTrack(format)
            out.start()
            val startUs = part.startMs * 1_000
            val endUs = part.endMs * 1_000
            // Every AAC frame decodes on its own, so a part may start at any frame.
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocate(format.maxInputSize())
            val info = MediaCodec.BufferInfo()
            while (true) {
                currentCoroutineContext().ensureActive()
                val size = extractor.readSampleData(buffer, 0)
                val timeUs = extractor.sampleTime
                if (size < 0 || timeUs >= endUs) break
                if (timeUs >= startUs) {
                    info.set(0, size, timeUs - startUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    out.writeSampleData(outTrack, buffer, info)
                }
                extractor.advance()
            }
            out.stop()
        } catch (e: IllegalStateException) {
            // MediaMuxer reports a bad format or an empty track this way.
            target.delete()
            throw IOException("Couldn't cut the sound", e)
        } catch (e: IOException) {
            target.delete()
            throw e
        } finally {
            try {
                muxer?.release()
            } catch (_: IllegalStateException) {
                // A muxer that failed to stop may refuse to release too; the failure is reported above.
            }
            extractor.release()
        }
    }

    private fun MediaFormat.maxInputSize(): Int =
        if (containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(MIN_BUFFER) else DEFAULT_BUFFER

    private companion object {
        const val MIN_BUFFER = 4 * 1024
        const val DEFAULT_BUFFER = 64 * 1024
    }
}
