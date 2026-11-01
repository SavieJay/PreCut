package com.savie.precut.model.ai.edit

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.effect.EffectType
import com.savie.precut.model.look.FilterCatalog
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.motion.ComboType
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.text.TextTemplates

/**
 * The names Claude uses for PreCut's built-in looks: filter and text template ids as they are,
 * enum entries in lower case ("fade_black"). The brief lists them, so a newer app version's
 * additions reach Claude without a server change.
 */
object EditCatalog {
    const val NONE = "none"

    val filters: List<String> get() = FilterCatalog.presets.map { it.id }
    val effects: List<String> = EffectType.entries.map { it.catalogId }
    val transitions: List<String> = TransitionType.entries.map { it.catalogId }
    val animations: List<String> = AnimationType.entries.map { it.catalogId }
    val combos: List<String> = ComboType.entries.map { it.catalogId }
    val textTemplates: List<String> get() = TextTemplates.all.map { it.id }

    fun effect(id: String): EffectType? = EffectType.entries.firstOrNull { it.catalogId == id }
    fun transition(id: String): TransitionType? = TransitionType.entries.firstOrNull { it.catalogId == id }
    fun animation(id: String): AnimationType? = AnimationType.entries.firstOrNull { it.catalogId == id }
    fun combo(id: String): ComboType? = ComboType.entries.firstOrNull { it.catalogId == id }

    /** "9:16", or "original". */
    fun ratioName(ratio: AspectRatio): String = when (ratio) {
        AspectRatio.ORIGINAL -> "original"
        AspectRatio.RATIO_235_100 -> "2.35:1"
        AspectRatio.RATIO_185_100 -> "1.85:1"
        AspectRatio.RATIO_9_19_5 -> "9:19.5"
        else -> "${ratio.widthRatio}:${ratio.heightRatio}"
    }

    fun ratio(name: String): AspectRatio? = AspectRatio.entries.firstOrNull { ratioName(it) == name }
}

/** An enum entry's catalog name. */
internal val Enum<*>.catalogId: String get() = name.lowercase()
