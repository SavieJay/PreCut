package com.savie.precut.media.export.gif

/**
 * A frame's colour table by median cut over a 15-bit colour histogram: the box with the most
 * pixels × widest spread is split at its median until there are enough colours; each colour is
 * the pixel-weighted mean of its box. Pixels map to their nearest colour, optionally with
 * Floyd–Steinberg dithering.
 */
class MedianCutPalette private constructor(val colours: IntArray) {

    val size: Int get() = colours.size

    /** Nearest colour per 15-bit key, filled as needed. */
    private val nearestCache = IntArray(BINS) { -1 }

    /**
     * Palette indices for [pixels] ([width] × [height]); pixels marked in [skip] get
     * [transparentIndex] and take no dithering error.
     */
    fun map(pixels: IntArray, width: Int, height: Int, skip: BooleanArray, dither: Boolean, transparentIndex: Int): ByteArray {
        val indices = ByteArray(width * height)
        if (!dither) {
            for (i in pixels.indices) {
                indices[i] = (if (skip[i]) transparentIndex else nearest(pixels[i] shr 16 and 0xFF, pixels[i] shr 8 and 0xFF, pixels[i] and 0xFF)).toByte()
            }
            return indices
        }
        // Error for this row and the next, per channel, with a pixel of margin on each side.
        var errThis = Array(3) { IntArray(width + 2) }
        var errNext = Array(3) { IntArray(width + 2) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (skip[i]) {
                    indices[i] = transparentIndex.toByte()
                    continue
                }
                val p = pixels[i]
                val r = clamp((p shr 16 and 0xFF) + errThis[0][x + 1] / 16)
                val g = clamp((p shr 8 and 0xFF) + errThis[1][x + 1] / 16)
                val b = clamp((p and 0xFF) + errThis[2][x + 1] / 16)
                val index = nearest(r, g, b)
                indices[i] = index.toByte()
                val c = colours[index]
                spread(errThis, errNext, x, r - (c shr 16 and 0xFF), 0)
                spread(errThis, errNext, x, g - (c shr 8 and 0xFF), 1)
                spread(errThis, errNext, x, b - (c and 0xFF), 2)
            }
            val done = errThis
            errThis = errNext
            errNext = done
            errNext.forEach { it.fill(0) }
        }
        return indices
    }

    /** Floyd–Steinberg: 7/16 right, 3/16 below left, 5/16 below, 1/16 below right (in 16ths). */
    private fun spread(errThis: Array<IntArray>, errNext: Array<IntArray>, x: Int, error: Int, channel: Int) {
        errThis[channel][x + 2] += error * 7
        errNext[channel][x] += error * 3
        errNext[channel][x + 1] += error * 5
        errNext[channel][x + 2] += error
    }

    fun nearest(r: Int, g: Int, b: Int): Int {
        val key = (r shr 3 shl 10) or (g shr 3 shl 5) or (b shr 3)
        val cached = nearestCache[key]
        if (cached >= 0) return cached
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (i in colours.indices) {
            val c = colours[i]
            val dr = (c shr 16 and 0xFF) - r
            val dg = (c shr 8 and 0xFF) - g
            val db = (c and 0xFF) - b
            // Weighted towards green, as eyes are.
            val distance = 2 * dr * dr + 4 * dg * dg + 3 * db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        nearestCache[key] = best
        return best
    }

    companion object {
        private const val BINS = 1 shl 15

        /** Up to [maxColours] colours for the [pixels] not marked in [skip]. */
        fun build(pixels: IntArray, skip: BooleanArray, maxColours: Int): MedianCutPalette {
            val count = IntArray(BINS)
            val sumR = IntArray(BINS)
            val sumG = IntArray(BINS)
            val sumB = IntArray(BINS)
            for (i in pixels.indices) {
                if (skip[i]) continue
                val p = pixels[i]
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                val key = (r shr 3 shl 10) or (g shr 3 shl 5) or (b shr 3)
                count[key]++
                sumR[key] += r
                sumG[key] += g
                sumB[key] += b
            }
            val keys = (0 until BINS).filter { count[it] > 0 }.toIntArray()
            if (keys.isEmpty()) return MedianCutPalette(intArrayOf(0xFF000000.toInt()))
            val boxes = ArrayList<IntRange>()
            boxes += keys.indices
            while (boxes.size < maxColours) {
                val (index, channel) = widestBox(boxes, keys, count) ?: break
                val box = boxes[index]
                val sorted = keys.copyOfRange(box.first, box.last + 1).sortedBy { channelOf(it, channel) }
                sorted.forEachIndexed { i, key -> keys[box.first + i] = key }
                val half = box.sumOf { count[keys[it]].toLong() } / 2
                var running = 0L
                var cut = box.first
                while (cut < box.last && running + count[keys[cut]] <= half) running += count[keys[cut++]]
                if (cut == box.first) cut++ // both halves keep at least one colour
                boxes[index] = box.first until cut
                boxes += cut..box.last
            }
            return MedianCutPalette(
                IntArray(boxes.size) { i ->
                    var n = 0L
                    var r = 0L
                    var g = 0L
                    var b = 0L
                    for (k in boxes[i]) {
                        val key = keys[k]
                        n += count[key]
                        r += sumR[key]
                        g += sumG[key]
                        b += sumB[key]
                    }
                    0xFF000000.toInt() or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
                },
            )
        }

        /** The box to split (most pixels × widest channel range) and that channel; null when none can split. */
        private fun widestBox(boxes: List<IntRange>, keys: IntArray, count: IntArray): Pair<Int, Int>? {
            var best: Pair<Int, Int>? = null
            var bestScore = 0L
            boxes.forEachIndexed { index, box ->
                if (box.last <= box.first) return@forEachIndexed
                var pixels = 0L
                val min = IntArray(3) { 31 }
                val max = IntArray(3)
                for (k in box) {
                    val key = keys[k]
                    pixels += count[key]
                    for (c in 0..2) {
                        val v = channelOf(key, c)
                        if (v < min[c]) min[c] = v
                        if (v > max[c]) max[c] = v
                    }
                }
                val channel = (0..2).maxBy { max[it] - min[it] }
                val score = pixels * (max[channel] - min[channel])
                if (score > bestScore) {
                    bestScore = score
                    best = index to channel
                }
            }
            return best
        }

        private fun channelOf(key: Int, channel: Int): Int = key shr (10 - channel * 5) and 0x1F

        private fun clamp(v: Int) = v.coerceIn(0, 255)
    }
}
