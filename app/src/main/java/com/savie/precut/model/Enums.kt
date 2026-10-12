package com.savie.precut.model

import kotlinx.serialization.Serializable

@Serializable
enum class AspectRatio(val widthRatio: Int, val heightRatio: Int) {
    RATIO_16_9(16, 9),
    RATIO_9_16(9, 16),
    RATIO_1_1(1, 1),
    RATIO_4_5(4, 5),
}

@Serializable
enum class ClipType { VIDEO, IMAGE }

@Serializable
enum class FilterType { NONE, BRIGHTNESS, CONTRAST, SATURATION, GRAYSCALE, WARM, COOL, VINTAGE }

@Serializable
enum class ExportQuality(val shortSidePx: Int) {
    P720(720),
    P1080(1080),
}
