package com.savie.precut.media.cutout

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Finds people in a picture with MediaPipe's selfie segmenter (bundled, Apache 2.0) run by LiteRT
 * on the phone's CPU: 256 × 256 RGB values 0..1 in, the chance each pixel is a person out. Nothing
 * leaves the phone. Not thread-safe: one picture at a time.
 */
class PersonSegmenter private constructor(private val interpreter: Interpreter) : Closeable {
    private val input: ByteBuffer = direct(PIXELS * 3)
    private val output: ByteBuffer = direct(PIXELS)
    private val inputFloats: FloatBuffer = input.asFloatBuffer()
    private val outputFloats: FloatBuffer = output.asFloatBuffer()

    /** [rgb] from [FrameSampler]; writes each pixel's person confidence (0..1) into [confidence]. */
    fun segment(rgb: FloatArray, confidence: FloatArray) {
        require(rgb.size == PIXELS * 3 && confidence.size == PIXELS) { "Wrong segmenter size" }
        inputFloats.clear()
        inputFloats.put(rgb)
        input.rewind()
        output.rewind()
        interpreter.run(input, output)
        outputFloats.rewind()
        outputFloats.get(confidence)
    }

    override fun close() = interpreter.close()

    companion object {
        /** The bundled model, in `assets/` (see docs/licenses). */
        const val MODEL_ASSET = "models/selfie_segmenter.tflite"

        /** Changes whenever the model or the matte recipe does, so old mattes aren't reused. */
        const val MATTE_VERSION = 1

        private const val PIXELS = SegmenterInput.SIDE * SegmenterInput.SIDE
        private const val THREADS = 4

        /** Loads the model; throws if it can't be read or LiteRT can't run on this phone. */
        fun create(context: Context): PersonSegmenter {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
            model.rewind()
            return PersonSegmenter(Interpreter(model, Interpreter.Options().setNumThreads(THREADS)))
        }

        private fun direct(floats: Int): ByteBuffer = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder())
    }
}
