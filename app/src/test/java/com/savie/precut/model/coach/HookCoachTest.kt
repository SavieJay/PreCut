package com.savie.precut.model.coach

import com.savie.precut.model.AudioClip
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.project
import com.savie.precut.model.text.CaptionInfo
import com.savie.precut.model.text.CaptionWord
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HookCoachTest {
    private val slow = project(videoClip("a"), videoClip("b"), videoClip("c")).copy(
        textOverlays = listOf(
            TextOverlay(id = "cap", text = "hello", startMs = 3_000L, endMs = 4_000L, yNorm = 0.8f, caption = CaptionInfo("g", listOf(CaptionWord("hello", 0L, 500L)))),
        ),
        audioClips = listOf(AudioClip(id = "m", uri = "m", sourceDurationMs = 60_000L, trimEndMs = 60_000L, beatsMs = (1..40).map { it * 1_000L })),
    )

    @Test
    fun `a slow quiet start with no title gets four tips, most useful first`() {
        assertEquals(
            listOf(HookTip.AddTitle, HookTip.CutQuietStart(2_850L), HookTip.TrimFirstClip("a", HOOK_MS), HookTip.ZoomInFirstClip("a")),
            slow.hookTips(),
        )
    }

    @Test
    fun `a strong start gets nothing`() {
        val strong = project(videoClip("a", sourceDurationMs = 2_000L), videoClip("b", sourceDurationMs = 2_000L)).let {
            it.copy(
                clips = it.clips.mapIndexed { i, clip -> if (i == 0) clip.copy(animationIn = com.savie.precut.model.motion.ClipAnimation(AnimationType.ZOOM)) else clip },
                textOverlays = listOf(TextOverlay(id = "t", text = "Wow", startMs = 0L, endMs = 1_500L)),
            )
        }
        assertTrue(strong.hookTips().isEmpty())
    }

    @Test
    fun `each tip's edit does what it says`() {
        val title = requireNotNull(slow.hookEdit(HookTip.AddTitle, "Watch this", "t1")) as EditAction.AddText
        assertEquals("Watch this", title.overlay.text)
        assertEquals(0L, title.overlay.startMs)
        val trimmed = EditReducer.reduce(slow, requireNotNull(slow.hookEdit(HookTip.TrimFirstClip("a", HOOK_MS), "", "x")))
        assertEquals(HOOK_MS, trimmed.clips.first().timelineDurationMs())
        val zoomed = EditReducer.reduce(slow, requireNotNull(slow.hookEdit(HookTip.ZoomInFirstClip("a"), "", "x")))
        assertEquals(AnimationType.ZOOM, zoomed.clips.first().animationIn?.type)
        val quiet = EditReducer.reduce(slow, requireNotNull(slow.hookEdit(HookTip.CutQuietStart(2_850L), "", "x")))
        assertEquals(150L, quiet.textOverlays.single().startMs)
        assertTrue(slow.hookEdit(HookTip.CutToBeats("m"), "", "x") is EditAction.Batch)
    }
}
