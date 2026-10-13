package com.savie.precut.model

import kotlinx.serialization.Serializable

/** Project frame shape, in picker order. [ORIGINAL] follows the first clip (see `Project.frameRatio`). */
@Serializable
enum class AspectRatio(val widthRatio: Int, val heightRatio: Int) {
    ORIGINAL(0, 0),
    RATIO_9_16(9, 16),
    RATIO_16_9(16, 9),
    RATIO_1_1(1, 1),
    RATIO_4_5(4, 5),
    RATIO_4_3(4, 3),
    RATIO_3_4(3, 4),
    RATIO_2_1(2, 1),
    RATIO_235_100(235, 100),
    RATIO_185_100(185, 100),
    /** Tall phones (9:19.5). */
    RATIO_9_19_5(18, 39),
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
