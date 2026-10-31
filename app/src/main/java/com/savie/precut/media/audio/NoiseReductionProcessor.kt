package com.savie.precut.media.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.savie.precut.model.audio.NoiseReduction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Reduce noise (Phase 22B) in a clip's audio chain, for preview and export alike: 16-bit or float
 * PCM through [SpectralDenoiser], same format out, exactly as many frames as went in (so sound stays
 * in sync). A seek starts it afresh.
 */
@OptIn(UnstableApi::class)
class NoiseReductionProcessor(private val reduction: NoiseReduction) : BaseAudioProcessor() {
    private var denoiser: SpectralDenoiser? = null
    private var samples = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val active = denoiser ?: newDenoiser().also { denoiser = it }
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val count = input.remaining() / if (float) 4 else 2
        if (samples.size < count) samples = FloatArray(count)
        for (i in 0 until count) samples[i] = if (float) input.getFloat() else input.getShort() / 32_768f
        active.process(samples, count / inputAudioFormat.channelCount)
        writeOutput(active)
    }

    override fun onQueueEndOfStream() {
        val active = denoiser ?: return
        active.finish()
        writeOutput(active)
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // Fresh each time: the format may have changed with it.
        denoiser = newDenoiser()
    }

    override fun onReset() {
        denoiser = null
        samples = FloatArray(0)
    }

    private fun newDenoiser() =
        SpectralDenoiser(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, reduction.profile, reduction.strength)

    private fun writeOutput(active: SpectralDenoiser) {
        active.drain { out, frames ->
            val total = frames * inputAudioFormat.channelCount
            val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
            val buffer = replaceOutputBuffer(total * if (float) 4 else 2)
            for (i in 0 until total) {
                if (float) {
                    buffer.putFloat(out[i])
                } else {
                    buffer.putShort((out[i] * 32_767f).roundToInt().coerceIn(-32_768, 32_767).toShort())
                }
            }
            buffer.flip()
        }
    }
}
