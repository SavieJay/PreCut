package com.savie.precut.media.cutout

import com.savie.precut.model.frame.PicturePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubjectFinderTest {

    /** A [w] × [h] picture with a person filling columns [left] until [right], rows [top] until [bottom]. */
    private fun person(w: Int, h: Int, left: Int, right: Int, top: Int, bottom: Int) =
        FloatArray(w * h) { i -> if (i % w in left until right && i / w in top until bottom) 0.9f else 0.1f }

    @Test
    fun centre_isAcrossTheMiddleAndTowardTheHead() {
        val point = SubjectFinder.centre(person(100, 100, left = 20, right = 40, top = 10, bottom = 90), 100, 100)
        // Columns 20–39 → their middle, 29.5 + 0.5 = 30 of 100; rows 10–89 → 40% down from the top.
        assertEquals(PicturePoint(0.3f, (10 + 0.4f * 79 + 0.5f) / 100f), point)
    }

    @Test
    fun tooLittlePerson_isNobody() {
        assertNull(SubjectFinder.centre(person(100, 100, 0, 1, 0, 2), 100, 100))
        assertNull(SubjectFinder.centre(FloatArray(64), 8, 8))
    }

    @Test
    fun mattePictures_read255AsPerson() {
        val matte = ByteArray(16) { i -> if (i % 4 >= 2) 255.toByte() else 0 }
        assertEquals(0.75f, SubjectFinder.centre(matte, 4, 4)?.x)
    }
}
