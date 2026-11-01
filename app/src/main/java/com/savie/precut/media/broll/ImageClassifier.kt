package com.savie.precut.media.broll

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Google's EfficientNet-Lite0 ImageNet classifier (MediaPipe's int8 model, Apache 2.0 per its own
 * metadata; `assets/models/image_classifier.tflite`) on LiteRT: 1,000 class scores for a picture.
 * Not thread-safe; close it after use.
 */
class ImageClassifier private constructor(private val interpreter: Interpreter) : Closeable {
    private val input = ByteBuffer.allocateDirect(SIDE * SIDE * 3).order(ByteOrder.nativeOrder())
    private val output = Array(1) { ByteArray(CLASSES) }
    private val pixels = IntArray(SIDE * SIDE)

    /** Scores 0..1 for each class of [picture], centre-cropped to a square first. */
    fun classify(picture: Bitmap): FloatArray {
        val side = minOf(picture.width, picture.height)
        val square = Bitmap.createBitmap(picture, (picture.width - side) / 2, (picture.height - side) / 2, side, side)
        val scaled = square.scale(SIDE, SIDE)
        scaled.getPixels(pixels, 0, SIDE, 0, 0, SIDE, SIDE)
        if (scaled !== square) scaled.recycle()
        if (square !== picture) square.recycle()
        input.rewind()
        // The model takes plain 0..255 RGB (its input quantization does the rest).
        for (p in pixels) {
            input.put((p shr 16 and 0xFF).toByte())
            input.put((p shr 8 and 0xFF).toByte())
            input.put((p and 0xFF).toByte())
        }
        input.rewind()
        interpreter.run(input, output)
        return FloatArray(CLASSES) { (output[0][it].toInt() and 0xFF) / 256f }
    }

    override fun close() = interpreter.close()

    companion object {
        const val MODEL_ASSET = "models/image_classifier.tflite"
        const val LABELS_ASSET = "models/imagenet_labels.txt"
        const val SIDE = 224
        private const val CLASSES = 1_000

        /** Loads the model; throws if it can't be read or LiteRT can't run on this phone. */
        fun create(context: Context): ImageClassifier {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
            model.rewind()
            return ImageClassifier(Interpreter(model, Interpreter.Options().setNumThreads(4)))
        }
    }
}
