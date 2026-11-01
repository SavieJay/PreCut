package com.savie.precut.model.broll

import com.savie.precut.model.ClipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrollSearchTest {
    private val names = File("src/main/assets/models/imagenet_labels.txt").readLines()

    private fun item(id: Long, vararg labels: Pair<Int, Float>) =
        GalleryItem(id, "content://$id", ClipType.IMAGE, 0L, 100, 100, 0, 0L, labels.map { it.first }, labels.map { it.second })

    private val beach = item(1, 978 to 0.7f, 975 to 0.1f)
    private val dog = item(2, names.indexOf("golden retriever") to 0.8f)
    private val dogOnBeach = item(3, names.indexOf("Labrador retriever") to 0.4f, 978 to 0.3f)
    private val pizza = item(4, names.indexOf("pizza") to 0.9f)

    @Test
    fun `the labels file is ImageNet's order, which the word groups rely on`() {
        assertEquals(1_000, names.size)
        assertEquals("seashore", names[978])
        assertEquals("Chihuahua", names[151])
        assertEquals("tabby", names[281])
    }

    @Test
    fun `everyday words find their classes, plurals too, and every word must match`() {
        val all = listOf(beach, dog, dogOnBeach, pizza)
        assertEquals(listOf(beach, dogOnBeach), searchGallery(all, "beach", names))
        assertEquals(listOf(dog, dogOnBeach), searchGallery(all, "Dogs", names))
        assertEquals(listOf(dogOnBeach), searchGallery(all, "dog on the beach", names).take(1))
        assertEquals(listOf(pizza), searchGallery(all, "pizza", names))
        assertEquals(listOf(pizza), searchGallery(all, "food", names))
        assertTrue(searchGallery(all, "  ", names).isEmpty())
    }

    @Test
    fun `only confident top classes are kept`() {
        val scores = FloatArray(1_000).also { it[5] = 0.6f; it[7] = 0.2f; it[9] = 0.01f }
        assertEquals(listOf(5, 7) to listOf(0.6f, 0.2f), topLabels(scores))
    }
}
