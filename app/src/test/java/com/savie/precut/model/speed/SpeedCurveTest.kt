package com.savie.precut.model.speed

import com.savie.precut.model.ClipType
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.project
import com.savie.precut.model.sourceToTimelineOffsetMs
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.timelineOffsetToSourceMs
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpeedCurveTest {

    private val ramp = SpeedCurve(listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 4f)))

    @Test
    fun speed_interpolatesOnALogScale() {
        assertEquals(2f, ramp.speedAt(0.5f), 1e-4f)
        assertEquals(1f, ramp.speedAt(-1f), 1e-6f)
        assertEquals(4f, ramp.speedAt(2f), 1e-6f)
    }

    @Test
    fun normalized_sortsClampsAndFixesTheEnds() {
        val messy = SpeedCurve(listOf(SpeedPoint(0.7f, 50f), SpeedPoint(0.2f, 0.01f), SpeedPoint(0.4f, 2f)))
        val clean = messy.normalized().points
        assertEquals(listOf(0f, 0.2f, 0.4f, 0.7f, 1f), clean.map { it.x })
        assertEquals(MIN_CURVE_SPEED, clean[1].speed)
        assertEquals(MAX_CURVE_SPEED, clean.last().speed)
    }

    @Test
    fun slice_keepsTheShape() {
        val halves = ramp.slice(0f, 0.5f) to ramp.slice(0.5f, 1f)
        assertEquals(2f, halves.first.points.last().speed, 1e-4f)
        assertEquals(2f, halves.second.points.first().speed, 1e-4f)
        assertEquals(ramp.speedAt(0.75f), halves.second.speedAt(0.5f), 1e-4f)
    }

    @Test
    fun steps_constantCurve_matchesConstantSpeed() {
        val steps = SpeedSteps.of(SpeedCurve(listOf(SpeedPoint(0f, 2f), SpeedPoint(1f, 2f))), 10_000_000L)
        assertEquals(5_000_000L, steps.timelineLengthUs())
        assertEquals(4_000_000L, steps.sourceAtUs(2_000_000.0))
        assertEquals(steps.count - 1, steps.stepAt(10_000_000L))
        assertEquals(1, steps.stepAt(steps.boundaryUs(1)))
    }

    @Test
    fun steps_mapBothWays() {
        val steps = SpeedSteps.of(SpeedCurve.preset(SpeedPreset.HERO), 6_000_000L)
        listOf(0L, 1_234_567L, 3_000_000L, 5_999_000L).forEach { source ->
            val back = steps.sourceAtUs(steps.timelineAtUs(source))
            assertTrue("$source → $back", abs(back - source) <= 1)
        }
    }

    @Test
    fun clip_lengthAndOffsets_followTheCurve() {
        val curve = SpeedCurve.preset(SpeedPreset.HERO)
        val hero = videoClip("a", 6_000L).copy(speedCurve = curve)
        val expected = Math.round(SpeedSteps.of(curve, 6_000_000L).timelineLengthUs() / 1_000.0)
        assertEquals(expected, hero.timelineDurationMs())
        assertTrue(hero.timelineDurationMs() < 6_000L) // mostly 3x
        val offset = 1_000L
        assertTrue(abs(offset - hero.sourceToTimelineOffsetMs(hero.timelineOffsetToSourceMs(offset))) <= 1)
    }

    @Test
    fun reducer_curvesAndConstantSpeedReplaceEachOther() {
        val base = project(videoClip("a"))
        val curved = EditReducer.reduce(base, EditAction.SetSpeedCurve("a", SpeedCurve.preset(SpeedPreset.BULLET)))
        assertEquals(SpeedPreset.BULLET, curved.clips[0].speedCurve?.preset)
        assertNull(EditReducer.reduce(curved, EditAction.SetSpeed("a", 2f)).clips[0].speedCurve)
        val photo = project(videoClip("p").copy(type = ClipType.IMAGE))
        assertSame(photo, EditReducer.reduce(photo, EditAction.SetSpeedCurve("p", ramp)))
    }

    @Test
    fun split_givesEachPartItsShare() {
        val clip = videoClip("a", 10_000L).copy(speedCurve = ramp)
        val split = EditReducer.reduce(project(clip), EditAction.SplitClip(clip.timelineDurationMs() / 2, "b")).clips
        assertEquals(1f, split[0].speedCurve?.points?.first()?.speed ?: 0f, 1e-4f)
        assertEquals(4f, split[1].speedCurve?.points?.last()?.speed ?: 0f, 1e-4f)
        // Re-sampling each half can only move the total by a step's rounding.
        val total = split.sumOf { it.timelineDurationMs() }
        assertTrue("$total vs ${clip.timelineDurationMs()}", abs(total - clip.timelineDurationMs()) < clip.timelineDurationMs() / 50)
    }
}
