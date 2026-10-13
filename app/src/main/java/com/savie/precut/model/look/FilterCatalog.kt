package com.savie.precut.model.look

/** Filter categories, in picker order. */
enum class FilterCategory { BASIC, LIFE, FILM, RETRO, MONO, FOOD, NATURE }

/** A built-in filter: a [Grade] baked into a LUT. Names live in the UI (by [id]). */
data class FilterPreset(val id: String, val category: FilterCategory, val grade: Grade)

/**
 * The built-in filters, generated in code (no licensed LUT files). "Basic" holds the Phase 4
 * filters with the same values Media3 effects used, so old projects look the same.
 */
object FilterCatalog {

    /** Prefix of imported `.cube` filters' ids. */
    const val CUSTOM_PREFIX = "custom:"

    private fun rgb(r: Float, g: Float, b: Float) = Rgb(r, g, b)

    /** Classic sepia blended 80/20 with the original, row-major (the Phase 4 Vintage matrix). */
    private val VINTAGE_MATRIX: List<Float> = run {
        val sepia = arrayOf(
            floatArrayOf(0.393f, 0.769f, 0.189f),
            floatArrayOf(0.349f, 0.686f, 0.168f),
            floatArrayOf(0.272f, 0.534f, 0.131f),
        )
        List(9) { i ->
            val row = i / 3
            val column = i % 3
            0.8f * sepia[row][column] + if (row == column) 0.2f else 0f
        }
    }

    private val SEPIA_FULL = listOf(0.393f, 0.769f, 0.189f, 0.349f, 0.686f, 0.168f, 0.272f, 0.534f, 0.131f)

    val presets: List<FilterPreset> = listOf(
        // Basic (Phase 4)
        FilterPreset("basic.bright", FilterCategory.BASIC, Grade(brightness = 0.12f)),
        FilterPreset("basic.contrast", FilterCategory.BASIC, Grade(contrast = 0.3f)),
        FilterPreset("basic.vivid", FilterCategory.BASIC, Grade(saturation = 0.45f)),
        FilterPreset("basic.mono", FilterCategory.BASIC, Grade(mono = 1f)),
        FilterPreset("basic.warm", FilterCategory.BASIC, Grade(gain = rgb(1.1f, 1f, 0.85f))),
        FilterPreset("basic.cool", FilterCategory.BASIC, Grade(gain = rgb(0.88f, 1f, 1.12f))),
        FilterPreset("basic.vintage", FilterCategory.BASIC, Grade(matrix = VINTAGE_MATRIX, contrast = -0.08f)),
        // Life
        FilterPreset("life.fresh", FilterCategory.LIFE, Grade(gain = rgb(0.98f, 1.03f, 1.04f), saturation = 0.12f, brightness = 0.03f)),
        FilterPreset("life.soft", FilterCategory.LIFE, Grade(contrast = -0.12f, fade = 0.25f, saturation = -0.05f, brightness = 0.03f)),
        FilterPreset("life.glow", FilterCategory.LIFE, Grade(gain = rgb(1.06f, 1.02f, 0.97f), gamma = rgb(1.12f, 1.1f, 1.08f), saturation = 0.08f)),
        FilterPreset("life.clear", FilterCategory.LIFE, Grade(contrast = 0.12f, saturation = 0.1f, gain = rgb(1f, 1.01f, 1.03f))),
        FilterPreset("life.peach", FilterCategory.LIFE, Grade(gain = rgb(1.08f, 1f, 0.95f), highlightTint = rgb(0.75f, 0.55f, 0.5f), split = 0.25f, fade = 0.1f)),
        FilterPreset("life.pastel", FilterCategory.LIFE, Grade(saturation = -0.15f, fade = 0.35f, brightness = 0.04f, highlightTint = rgb(0.6f, 0.5f, 0.65f), split = 0.15f)),
        // Film
        FilterPreset(
            "film.teal_orange", FilterCategory.FILM,
            Grade(shadowTint = rgb(0.2f, 0.55f, 0.65f), highlightTint = rgb(0.85f, 0.6f, 0.35f), split = 0.45f, contrast = 0.1f),
        ),
        FilterPreset("film.kodak", FilterCategory.FILM, Grade(gain = rgb(1.06f, 1.01f, 0.92f), lift = rgb(0.03f, 0.02f, 0f), contrast = 0.12f, saturation = 0.08f)),
        FilterPreset("film.fuji", FilterCategory.FILM, Grade(gain = rgb(0.97f, 1.03f, 1f), shadowTint = rgb(0.4f, 0.6f, 0.55f), split = 0.25f, contrast = 0.08f)),
        FilterPreset("film.cinematic", FilterCategory.FILM, Grade(contrast = 0.22f, saturation = -0.12f, shadowTint = rgb(0.3f, 0.45f, 0.6f), split = 0.3f)),
        FilterPreset("film.moody", FilterCategory.FILM, Grade(gamma = rgb(0.9f, 0.9f, 0.92f), saturation = -0.2f, fade = 0.15f, shadowTint = rgb(0.35f, 0.45f, 0.5f), split = 0.3f)),
        FilterPreset("film.bleach", FilterCategory.FILM, Grade(mono = 0.45f, contrast = 0.3f)),
        FilterPreset("film.matte", FilterCategory.FILM, Grade(fade = 0.45f, contrast = 0.05f, saturation = -0.08f)),
        // Retro
        FilterPreset("retro.70s", FilterCategory.RETRO, Grade(gain = rgb(1.1f, 1f, 0.8f), fade = 0.25f, saturation = -0.1f, lift = rgb(0.04f, 0.02f, 0f))),
        FilterPreset("retro.polaroid", FilterCategory.RETRO, Grade(lift = rgb(0.05f, 0.05f, 0.08f), gain = rgb(1.05f, 1.02f, 0.95f), fade = 0.2f, contrast = -0.05f)),
        FilterPreset("retro.faded", FilterCategory.RETRO, Grade(fade = 0.55f, saturation = -0.25f)),
        FilterPreset("retro.sepia", FilterCategory.RETRO, Grade(matrix = SEPIA_FULL)),
        FilterPreset("retro.cross", FilterCategory.RETRO, Grade(gamma = rgb(1.15f, 1.1f, 0.8f), gain = rgb(1.05f, 1.05f, 0.9f), contrast = 0.15f, saturation = 0.1f)),
        FilterPreset("retro.vhs", FilterCategory.RETRO, Grade(gain = rgb(1.02f, 0.95f, 1.08f), fade = 0.2f, saturation = 0.12f, contrast = 0.08f)),
        // Mono
        FilterPreset("mono.high_contrast", FilterCategory.MONO, Grade(mono = 1f, contrast = 0.4f)),
        FilterPreset("mono.soft", FilterCategory.MONO, Grade(mono = 1f, contrast = -0.15f, fade = 0.2f)),
        FilterPreset("mono.silver", FilterCategory.MONO, Grade(mono = 1f, gamma = rgb(1.15f, 1.15f, 1.15f), contrast = 0.1f)),
        FilterPreset("mono.selenium", FilterCategory.MONO, Grade(mono = 1f, shadowTint = rgb(0.4f, 0.45f, 0.6f), split = 0.4f)),
        FilterPreset("mono.noir", FilterCategory.MONO, Grade(mono = 1f, contrast = 0.55f, gamma = rgb(0.85f, 0.85f, 0.85f))),
        // Food
        FilterPreset("food.warm_glow", FilterCategory.FOOD, Grade(gain = rgb(1.08f, 1.02f, 0.9f), saturation = 0.15f, brightness = 0.03f)),
        FilterPreset("food.fresh_green", FilterCategory.FOOD, Grade(gain = rgb(0.98f, 1.06f, 0.98f), saturation = 0.18f, contrast = 0.05f)),
        FilterPreset("food.rich", FilterCategory.FOOD, Grade(contrast = 0.18f, saturation = 0.25f, gamma = rgb(0.95f, 0.95f, 0.95f))),
        FilterPreset("food.sweet", FilterCategory.FOOD, Grade(gain = rgb(1.06f, 1f, 1.02f), highlightTint = rgb(0.7f, 0.5f, 0.6f), split = 0.2f, brightness = 0.04f)),
        FilterPreset("food.crisp", FilterCategory.FOOD, Grade(contrast = 0.2f, saturation = 0.1f, gain = rgb(1f, 1f, 1.03f))),
        // Nature
        FilterPreset("nature.lush", FilterCategory.NATURE, Grade(gain = rgb(0.96f, 1.08f, 0.96f), saturation = 0.2f)),
        FilterPreset("nature.autumn", FilterCategory.NATURE, Grade(gain = rgb(1.12f, 1f, 0.82f), saturation = 0.15f, contrast = 0.08f)),
        FilterPreset("nature.ocean", FilterCategory.NATURE, Grade(gain = rgb(0.9f, 1.02f, 1.12f), saturation = 0.15f)),
        FilterPreset("nature.sunset", FilterCategory.NATURE, Grade(highlightTint = rgb(0.9f, 0.55f, 0.3f), shadowTint = rgb(0.45f, 0.35f, 0.6f), split = 0.4f, saturation = 0.1f)),
        FilterPreset("nature.forest", FilterCategory.NATURE, Grade(gain = rgb(0.95f, 1.04f, 0.97f), gamma = rgb(0.95f, 0.95f, 0.95f), saturation = -0.05f, fade = 0.1f)),
        FilterPreset("nature.sky", FilterCategory.NATURE, Grade(gain = rgb(0.97f, 1f, 1.1f), contrast = 0.1f, saturation = 0.12f)),
    )

    private val byId: Map<String, FilterPreset> = presets.associateBy { it.id }

    fun preset(id: String): FilterPreset? = byId[id]

    fun inCategory(category: FilterCategory): List<FilterPreset> = presets.filter { it.category == category }

    fun isCustom(id: String): Boolean = id.startsWith(CUSTOM_PREFIX)

    /** The Phase 4 [FilterType] names, for migrating old drafts. */
    val LEGACY_IDS: Map<String, String> = mapOf(
        "BRIGHTNESS" to "basic.bright",
        "CONTRAST" to "basic.contrast",
        "SATURATION" to "basic.vivid",
        "GRAYSCALE" to "basic.mono",
        "WARM" to "basic.warm",
        "COOL" to "basic.cool",
        "VINTAGE" to "basic.vintage",
    )
}
