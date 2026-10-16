package com.savie.precut.model.sticker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {
    @Test
    fun search_matchesWordStarts() {
        assertTrue(EmojiCatalog.search("jol").map { it.emoji }.containsAll(listOf("🥘", "🍛")))
        assertEquals(listOf("🔥"), EmojiCatalog.search("FIRE").map { it.emoji })
        assertEquals(emptyList<EmojiEntry>(), EmojiCatalog.search("  "))
        // Every word must match.
        assertEquals(listOf("💔"), EmojiCatalog.search("broken heart").map { it.emoji })
    }

    @Test
    fun catalogue_hasNoDuplicates() {
        val all = EmojiCatalog.categories.values.flatten().map { it.emoji }
        assertEquals(all.size, all.toSet().size)
        assertEquals(EmojiCategory.entries.toSet(), EmojiCatalog.categories.keys)
    }
}
