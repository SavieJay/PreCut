package com.savie.precut.model.edit

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.frame.PicturePoint
import com.savie.precut.model.frame.SubjectSample
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatVariantTest {
    private val wide = videoClip("w").copy(sourceWidth = 1920, sourceHeight = 1080)
    private val tall = videoClip("t").copy(sourceWidth = 1080, sourceHeight = 1920)
    private val samples = mapOf(
        "w" to (0..4).map { SubjectSample(it * 1_000L, PicturePoint(0.8f, 0.5f)) },
        "t" to (0..4).map { SubjectSample(it * 1_000L, PicturePoint(0.5f, 0.5f)) },
    )
    private val base = project(wide, tall).copy(aspectRatio = AspectRatio.RATIO_16_9)

    @Test
    fun `each shape reframes the clips that don't fit it, on their person`() {
        val upright = base.formatVariant(AspectRatio.RATIO_9_16, samples)
        assertEquals(AspectRatio.RATIO_9_16, upright.aspectRatio)
        val reframed = upright.clips.first { it.id == "w" }
        assertEquals(FrameFit.FILL, reframed.fit)
        // The person sits right of centre in the wide picture: the frame moves that way (offset left).
        assert(reframed.transform.offsetX < 0f)
        // The tall clip already fits the upright frame.
        assertEquals(tall, upright.clips.first { it.id == "t" })
    }

    @Test
    fun `without samples the shape changes and the clips stay`() {
        val square = base.formatVariant(AspectRatio.RATIO_1_1, emptyMap())
        assertEquals(AspectRatio.RATIO_1_1, square.aspectRatio)
        assertEquals(base.clips, square.clips)
        assertEquals(listOf(AspectRatio.RATIO_9_16, AspectRatio.RATIO_1_1, AspectRatio.RATIO_16_9), EXPORT_FORMATS)
    }
}
