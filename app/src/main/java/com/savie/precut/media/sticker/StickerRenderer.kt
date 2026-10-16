package com.savie.precut.media.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.text.TextPaint
import android.util.LruCache
import com.savie.precut.model.sticker.Sticker
import com.savie.precut.model.sticker.StickerKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** A GIF sticker's frames at one size, looping. */
class StickerFrames(private val bitmaps: List<Bitmap>, private val delaysMs: IntArray) {
    private val totalMs = delaysMs.sum().coerceAtLeast(1)

    /** The frame shown [localMs] after the sticker appears. */
    fun at(localMs: Long): Bitmap {
        var t = (localMs.coerceAtLeast(0L) % totalMs).toInt()
        bitmaps.indices.forEach { i ->
            t -= delaysMs[i]
            if (t < 0) return bitmaps[i]
        }
        return bitmaps.last()
    }
}

/**
 * Draws stickers (S-01, S-02) at a given height in output pixels: an emoji with the system's
 * colour emoji font, or a picture (PNG/WebP/JPEG/GIF first frame; GIF frames via [frames]).
 * Decoded pictures are cached by Uri and size. Safe to call from any thread.
 */
@Singleton
class StickerRenderer @Inject constructor(@ApplicationContext private val context: Context) {

    private val stills = object : LruCache<String, Bitmap>(STILL_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val animations = object : LruCache<String, Pair<StickerFrames, Int>>(FRAME_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Pair<StickerFrames, Int>) = value.second
    }

    fun render(sticker: Sticker, heightPx: Float): Bitmap {
        val height = heightPx.roundToInt().coerceIn(1, MAX_SIDE)
        val key = "${sticker.kind}|${sticker.value}|$height"
        stills.get(key)?.let { return it }
        val bitmap = when (sticker.kind) {
            StickerKind.EMOJI -> emoji(sticker.value, height)
            StickerKind.IMAGE, StickerKind.DRAWING -> picture(sticker.value, height) ?: empty(sticker.aspect, height)
        }
        stills.put(key, bitmap)
        return bitmap
    }

    /** A GIF sticker's frames at [heightPx]; null for a still sticker or an unreadable file. */
    fun frames(sticker: Sticker, heightPx: Float): StickerFrames? {
        if (!sticker.animated || sticker.kind == StickerKind.EMOJI) return null
        val height = heightPx.roundToInt().coerceIn(1, MAX_SIDE)
        val key = "${sticker.value}|$height"
        animations.get(key)?.let { return it.first }
        val bytes = read(sticker.value) ?: return null
        val bitmaps = mutableListOf<Bitmap>()
        val delays = mutableListOf<Int>()
        var budget = FRAME_BUDGET_BYTES
        val info = GifDecoder.decode(bytes, MAX_FRAMES) { pixels, delayMs ->
            if (budget <= 0) {
                // Out of memory budget: hold the last frame longer instead.
                if (delays.isNotEmpty()) delays[delays.lastIndex] += delayMs
                return@decode
            }
            bitmaps += scaledFrame(pixels, bytes, height) ?: return@decode
            delays += delayMs
            budget -= bitmaps.last().allocationByteCount
        } ?: return null
        if (bitmaps.size < 2 || info.frameCount < 2) return null
        val frames = StickerFrames(bitmaps, delays.toIntArray())
        animations.put(key, frames to bitmaps.sumOf { it.allocationByteCount })
        return frames
    }

    /** A GIF's size and whether it has more than one frame (import checks); null when not a GIF. */
    fun gifInfo(bytes: ByteArray): GifInfo? = GifDecoder.decode(bytes, maxFrames = 2) { _, _ -> }

    private fun scaledFrame(pixels: IntArray, bytes: ByteArray, height: Int): Bitmap? {
        // The size is in the GIF's screen descriptor (bytes 6–9, little-endian).
        val w = (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8)
        val h = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
        if (w * h != pixels.size) return null
        val full = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        val width = max(1, (w.toFloat() / h * height).roundToInt()).coerceAtMost(MAX_SIDE)
        if (width == w && height == h) return full
        return Bitmap.createScaledBitmap(full, width, height, true).also { if (it !== full) full.recycle() }
    }

    private fun emoji(text: String, height: Int): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = height * EMOJI_FILL }
        val metrics = paint.fontMetrics
        val width = ceil(paint.measureText(text)).toInt().coerceIn(1, MAX_SIDE)
        val bitmap = Bitmap.createBitmap(max(width, 1), height, Bitmap.Config.ARGB_8888)
        // Centre the glyph box vertically.
        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
        Canvas(bitmap).drawText(text, 0f, baseline, paint)
        return bitmap
    }

    private fun picture(uri: String, height: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri) { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= height) sample *= 2
        val decoded = open(uri) { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val width = max(1, (decoded.width.toFloat() / decoded.height * height).roundToInt()).coerceAtMost(MAX_SIDE)
        if (decoded.width == width && decoded.height == height) return decoded
        return Bitmap.createScaledBitmap(decoded, width, height, true).also { if (it !== decoded) decoded.recycle() }
    }

    /** A clear stand-in for a picture that's gone, so the sticker keeps its box. */
    private fun empty(aspect: Float, height: Int): Bitmap =
        Bitmap.createBitmap(max(1, (aspect * height).roundToInt()).coerceAtMost(MAX_SIDE), height, Bitmap.Config.ARGB_8888)

    private fun read(uri: String): ByteArray? = open(uri) { it.readBytes() }

    private fun <T> open(uri: String, block: (java.io.InputStream) -> T?): T? = try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use(block)
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private companion object {
        const val MAX_SIDE = 4_096
        /** The emoji glyph's size as a share of the sticker height. */
        const val EMOJI_FILL = 0.84f
        const val MAX_FRAMES = 300
        const val STILL_CACHE_BYTES = 48 * 1024 * 1024
        const val FRAME_CACHE_BYTES = 96 * 1024 * 1024
        /** One GIF's frames at one size. */
        const val FRAME_BUDGET_BYTES = 64 * 1024 * 1024
    }
}
