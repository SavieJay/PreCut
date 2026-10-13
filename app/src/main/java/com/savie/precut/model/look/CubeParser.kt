package com.savie.precut.model.look

/**
 * Parses Adobe/Resolve `.cube` 3D LUTs: `LUT_3D_SIZE`, optional `TITLE` and a 0..1 `DOMAIN_MIN/MAX`,
 * `#` comments, then size³ lines of "r g b" with red changing fastest. 1D LUTs aren't supported.
 */
object CubeParser {

    /** Largest cube accepted (a 65³ LUT is the largest common size). */
    const val MAX_SIZE = 65

    fun parse(text: String): Result<Cube> = runCatching {
        var size = 0
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        val values = ArrayList<Float>()
        text.lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val parts = line.split(Regex("\\s+"))
            when (parts[0].uppercase()) {
                "TITLE" -> Unit
                "LUT_1D_SIZE" -> throw IllegalArgumentException("1D LUTs aren't supported")
                "LUT_3D_SIZE" -> size = parts.getOrNull(1)?.toIntOrNull() ?: throw IllegalArgumentException("Bad LUT_3D_SIZE")
                "DOMAIN_MIN" -> domainMin = floats(parts)
                "DOMAIN_MAX" -> domainMax = floats(parts)
                "LUT_3D_INPUT_RANGE", "LUT_1D_INPUT_RANGE" -> Unit
                else -> {
                    require(parts.size == 3) { "Bad data line: $line" }
                    parts.forEach { values += it.toFloatOrNull() ?: throw IllegalArgumentException("Bad number: $it") }
                }
            }
        }
        require(size in 2..MAX_SIZE) { "Missing or unsupported LUT_3D_SIZE" }
        require(values.size == size * size * size * 3) { "Expected ${size * size * size} entries, found ${values.size / 3}" }
        // The domain is the input range; PreCut samples 0..1 (what nearly every LUT uses).
        require(domainMin.all { it == 0f } && domainMax.all { it == 1f }) { "Only a 0..1 input domain is supported" }
        Cube(size, FloatArray(values.size) { values[it].coerceIn(0f, 1f) })
    }

    private fun floats(parts: List<String>): FloatArray {
        require(parts.size == 4) { "Bad domain: $parts" }
        return FloatArray(3) { parts[it + 1].toFloatOrNull() ?: throw IllegalArgumentException("Bad domain value") }
    }
}
