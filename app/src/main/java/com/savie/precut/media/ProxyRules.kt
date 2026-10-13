package com.savie.precut.media

import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import java.security.MessageDigest

/**
 * Which clips get a preview proxy and what it's called. Pure, so it can be unit-tested.
 *
 * Proxies are small preview copies with frequent keyframes (CapCut's approach): seeking anywhere
 * decodes only a few small frames, so scrubbing follows the finger. Export always uses originals.
 */
object ProxyRules {
    /** Short side of a proxy. */
    const val PROXY_SHORT_SIDE_PX = 540

    /** A keyframe this often (seconds) keeps every seek a few frames from a keyframe. */
    const val PROXY_KEYFRAME_INTERVAL_S = 0.2f

    const val PROXY_BITRATE = 2_500_000

    /** Oldest proxies are deleted beyond this. */
    const val PROXY_CACHE_LIMIT_BYTES = 2L * 1024 * 1024 * 1024

    /**
     * Videos larger than a proxy get one (unknown sizes too, to be safe); videos already this
     * small decode fast enough, and photos are decoded once.
     */
    fun needsProxy(clip: Clip): Boolean {
        if (clip.type != ClipType.VIDEO) return false
        val shortSide = minOf(clip.sourceWidth, clip.sourceHeight)
        return shortSide <= 0 || shortSide > PROXY_SHORT_SIDE_PX
    }

    /** Stable file name for a source Uri's proxy. */
    fun fileName(sourceUri: String): String =
        MessageDigest.getInstance("SHA-1").digest(sourceUri.toByteArray())
            .joinToString("") { "%02x".format(it) } + ".mp4"

    /** A cached proxy file: [name], [sizeBytes] and [lastModifiedMs]. */
    data class CachedFile(val name: String, val sizeBytes: Long, val lastModifiedMs: Long)

    /**
     * Proxies to delete: those not in [keepNames], then the oldest kept ones until the total
     * fits in [limitBytes].
     */
    fun filesToDelete(files: List<CachedFile>, keepNames: Set<String>, limitBytes: Long): List<String> {
        val (kept, orphaned) = files.partition { it.name in keepNames }
        var total = kept.sumOf { it.sizeBytes }
        val evicted = kept.sortedBy { it.lastModifiedMs }.takeWhile { file ->
            (total > limitBytes).also { over -> if (over) total -= file.sizeBytes }
        }
        return (orphaned + evicted).map { it.name }
    }
}
