package com.savie.precut.media.look

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Filter picker tiles: a clip's thumbnail graded on the CPU with the same LUT the shader uses, so
 * each tile shows what the filter really does.
 */
@Singleton
class LookThumbnailer @Inject constructor(private val luts: LutCache) {

    private val cache = LruCache<Pair<Int, String>, Bitmap>(MAX_CACHED)

    /** [base] graded by [filterId], or null if the filter can't be loaded. */
    suspend fun graded(base: Bitmap, filterId: String): Bitmap? {
        val key = System.identityHashCode(base) to filterId
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            val cube = luts.cube(filterId) ?: return@withContext null
            val pixels = IntArray(base.width * base.height)
            base.getPixels(pixels, 0, base.width, 0, 0, base.width, base.height)
            for (i in pixels.indices) {
                val p = pixels[i]
                val out = cube.lookup(((p shr 16) and 0xFF) / 255f, ((p shr 8) and 0xFF) / 255f, (p and 0xFF) / 255f)
                pixels[i] = (p and 0xFF000000.toInt()) or
                    (channel(out.r) shl 16) or (channel(out.g) shl 8) or channel(out.b)
            }
            Bitmap.createBitmap(pixels, base.width, base.height, Bitmap.Config.ARGB_8888).also { cache.put(key, it) }
        }
    }

    private fun channel(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

    private companion object {
        const val MAX_CACHED = 96
    }
}
