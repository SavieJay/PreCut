package com.savie.precut.media

import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.videoClip
import com.savie.precut.media.ProxyRules.CachedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRulesTest {

    @Test
    fun onlyVideosLargerThanAProxyGetOne() {
        assertTrue(ProxyRules.needsProxy(videoClip().copy(sourceWidth = 1920, sourceHeight = 1080)))
        assertTrue(ProxyRules.needsProxy(videoClip())) // unknown size: proxy to be safe
        assertFalse(ProxyRules.needsProxy(videoClip().copy(sourceWidth = 960, sourceHeight = 540)))
        assertFalse(ProxyRules.needsProxy(clipFromMedia("p", "content://p", MediaInfo(ClipType.IMAGE, 0L, 4000, 3000, 0))))
    }

    @Test
    fun fileName_isStablePerSource() {
        assertEquals(ProxyRules.fileName("content://a"), ProxyRules.fileName("content://a"))
        assertNotEquals(ProxyRules.fileName("content://a"), ProxyRules.fileName("content://b"))
        assertTrue(ProxyRules.fileName("content://a").matches(Regex("[0-9a-f]{40}\\.mp4")))
    }

    @Test
    fun filesToDelete_orphansThenOldestBeyondLimit() {
        val files = listOf(
            CachedFile("orphan", sizeBytes = 10, lastModifiedMs = 9),
            CachedFile("old", sizeBytes = 50, lastModifiedMs = 1),
            CachedFile("mid", sizeBytes = 50, lastModifiedMs = 2),
            CachedFile("new", sizeBytes = 50, lastModifiedMs = 3),
        )
        val keep = setOf("old", "mid", "new")
        assertEquals(listOf("orphan"), ProxyRules.filesToDelete(files, keep, limitBytes = 150))
        assertEquals(listOf("orphan", "old"), ProxyRules.filesToDelete(files, keep, limitBytes = 120))
        assertEquals(listOf("orphan", "old", "mid"), ProxyRules.filesToDelete(files, keep, limitBytes = 50))
    }
}
