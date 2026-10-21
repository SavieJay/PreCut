package com.savie.precut.media.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder

/** Decodes a file's first audio track to 16-bit PCM, in order (MediaExtractor + MediaCodec). */
internal object PcmDecoder {
    private const val TIMEOUT_US = 10_000L

    /**
     * Calls [onFormat] with the sample rate and channel count before any samples, and again if the
     * decoder's real output differs from the container's; then [onPcm] for each chunk of
     * interleaved samples with its presentation time. False when [uri] has no audio track. Throws
     * `IOException` or a `RuntimeException` (unsupported codec, revoked access, corrupt file).
     */
    suspend fun decode(
        context: Context,
        uri: String,
        onFormat: (sampleRate: Int, channels: Int) -> Unit,
        onPcm: (samples: ShortArray, count: Int, timeUs: Long) -> Unit,
    ): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri.toUri(), null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            onFormat(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            drain(extractor, decoder, onFormat, onPcm)
            return true
        } finally {
            codec?.release()
            extractor.release()
        }
    }

    private suspend fun drain(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        onFormat: (Int, Int) -> Unit,
        onPcm: (ShortArray, Int, Long) -> Unit,
    ) {
        val info = MediaCodec.BufferInfo()
        var shorts = ShortArray(8192)
        var inputDone = false
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = requireNotNull(decoder.getInputBuffer(inIndex))
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // The decoder's real output rate/channels (it may differ from the container's).
                onFormat(decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            } else if (outIndex >= 0) {
                val output = requireNotNull(decoder.getOutputBuffer(outIndex))
                val pcm = output.order(ByteOrder.nativeOrder()).asShortBuffer()
                val count = info.size / 2
                if (shorts.size < count) shorts = ShortArray(count)
                pcm.get(shorts, 0, count)
                if (count > 0) onPcm(shorts, count, info.presentationTimeUs)
                decoder.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
    }
}
