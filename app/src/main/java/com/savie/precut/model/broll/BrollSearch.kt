package com.savie.precut.model.broll

import com.savie.precut.model.ClipType
import kotlinx.serialization.Serializable

/**
 * Smart B-roll (Phase 26B): a photo or video from the gallery, with what the on-phone classifier
 * saw in it ([labels], ImageNet class indexes, with their [scores] 0..1). Kept between uses, by
 * MediaStore id and modification time.
 */
@Serializable
data class GalleryItem(
    val id: Long,
    val uri: String,
    val type: ClipType,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    val modifiedSec: Long,
    val labels: List<Int>,
    val scores: List<Float>,
)

/**
 * Everyday words for groups of ImageNet's 1,000 classes (the classifier's own names are breeds and
 * objects: "golden retriever", "seashore"). A search word matches its group, or any class whose
 * name contains it.
 */
object BrollWords {
    private fun range(range: IntRange) = range.toSet()

    private val DOG = range(151..268)
    private val CAT = range(281..293)
    private val BIRD = range(7..24) + range(80..100) + range(127..146)
    private val FISH = range(0..6) + range(389..397)
    private val INSECT = range(300..326)
    private val MONKEY = range(365..384)
    private val FOOD = range(923..969)
    private val FRUIT = range(948..957)
    private val BEACH = setOf(978, 977, 975, 976)
    private val MOUNTAIN = setOf(970, 972, 979, 980, 976)
    private val BOAT = setOf(472, 484, 510, 554, 576, 625, 628, 724, 780, 814, 833, 871, 914, 693, 403)
    private val CAR = setOf(407, 436, 468, 511, 609, 627, 656, 661, 717, 751, 817, 734, 675, 864, 867, 479, 581)
    private val FLOWER = setOf(985, 986, 738)
    private val WEDDING = setOf(982, 578, 834)
    private val BIKE = setOf(671, 444, 870, 880, 665, 670)
    private val PLANE = setOf(404, 895, 405, 908)
    private val MUSIC = setOf(402, 546, 420, 889, 486, 579, 881, 541, 650, 819)
    private val SPORT = setOf(430, 805, 560, 768, 852, 890, 574, 429, 722, 795, 802)
    private val PARTY = setOf(417, 470, 509)
    private val CITY = setOf(698, 663, 497, 668, 821, 839, 919, 920, 829, 874, 498, 624, 762, 415, 860, 865, 788, 582, 424)
    private val HOME = setOf(449, 598, 706, 831, 532, 905, 564)
    private val SNOW = setOf(970, 795, 802, 537, 803)
    private val COFFEE = setOf(967, 968, 504, 505)

    /** Search words (lower case, singular) and their classes. */
    val GROUPS: Map<String, Set<Int>> = mapOf(
        "dog" to DOG, "puppy" to DOG, "cat" to CAT, "kitten" to CAT, "bird" to BIRD, "fish" to FISH,
        "insect" to INSECT, "butterfly" to range(321..326), "monkey" to MONKEY, "animal" to DOG + CAT + BIRD + MONKEY,
        "food" to FOOD, "meal" to FOOD, "fruit" to FRUIT, "beach" to BEACH, "sea" to BEACH, "ocean" to BEACH,
        "coast" to BEACH, "lake" to setOf(975), "mountain" to MOUNTAIN, "hill" to MOUNTAIN, "nature" to BEACH + MOUNTAIN + FLOWER,
        "boat" to BOAT, "ship" to BOAT, "car" to CAR, "flower" to FLOWER, "wedding" to WEDDING, "bike" to BIKE,
        "bicycle" to BIKE, "plane" to PLANE, "travel" to PLANE + BOAT + BEACH + MOUNTAIN, "music" to MUSIC,
        "guitar" to setOf(402, 546), "sport" to SPORT, "ball" to SPORT, "party" to PARTY, "birthday" to PARTY,
        "city" to CITY, "street" to CITY, "shop" to CITY, "home" to HOME, "house" to HOME, "snow" to SNOW,
        "winter" to SNOW, "coffee" to COFFEE, "drink" to COFFEE,
    )
}

/** Words that join a search ("dog on the beach") rather than name something. */
private val STOP_WORDS = setOf("the", "a", "an", "on", "in", "at", "of", "with", "and", "my", "by", "to", "for", "from", "some", "our")

/** The least score a class needs to count. */
const val MIN_LABEL_SCORE = 0.05f

/**
 * [items] matching [query], best first: each word (plurals trimmed of a final "s") scores the best
 * of an item's classes in its group or whose name ([labelNames]) contains it, and an item must
 * match every word that names something ("on", "the" and unknown words are skipped). Empty when no
 * word names anything.
 */
fun searchGallery(items: List<GalleryItem>, query: String, labelNames: List<String>): List<GalleryItem> {
    val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 && it !in STOP_WORDS }
    if (words.isEmpty()) return emptyList()
    fun classes(word: String): Set<Int> {
        val singular = if (word.length > 3 && word.endsWith("s")) word.dropLast(1) else word
        val named = labelNames.indices.filter { i -> labelNames[i].lowercase().split(' ', '-', '\'').any { it == word || it == singular } }
        return (BrollWords.GROUPS[word] ?: BrollWords.GROUPS[singular].orEmpty()) + named
    }
    val wanted = words.map(::classes).filter { it.isNotEmpty() }
    if (wanted.isEmpty()) return emptyList()
    return items.mapNotNull { item ->
        val perWord = wanted.map { set -> item.labels.indices.filter { item.labels[it] in set }.maxOfOrNull { item.scores[it] } ?: 0f }
        if (perWord.any { it < MIN_LABEL_SCORE }) null else item to perWord.sum()
    }.sortedByDescending { it.second }.map { it.first }
}

/** The classes worth keeping from a classifier's [scores]: the top [count] above [MIN_LABEL_SCORE]. */
fun topLabels(scores: FloatArray, count: Int = 5): Pair<List<Int>, List<Float>> {
    val top = scores.indices.sortedByDescending { scores[it] }.take(count).filter { scores[it] >= MIN_LABEL_SCORE }
    return top to top.map { scores[it] }
}
