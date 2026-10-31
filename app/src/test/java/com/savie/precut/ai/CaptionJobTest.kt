package com.savie.precut.ai

import com.savie.precut.media.ExportState
import com.savie.precut.media.SpeechAudio
import com.savie.precut.media.VideoExporter
import com.savie.precut.model.AudioClip
import com.savie.precut.model.AudioKind
import com.savie.precut.model.Project
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.ai.SpeechPart
import com.savie.precut.model.ai.Transcript
import com.savie.precut.model.ai.TranscriptWord
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.export.GifSettings
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CaptionJobTest {
    private lateinit var dir: File
    private val exporter = FakeExporter()
    private val speech = FakeSpeechAudio()
    private val transcriber = FakeTranscriber()
    private val job = CaptionJob(exporter, speech, transcriber, Dispatchers.IO)
    private val minute = 60_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("captions").toFile()
        exporter.dir = dir
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun video(lengthMs: Long): Project {
        val music = AudioClip("m", "content://m", lengthMs, trimEndMs = lengthMs, kind = AudioKind.MUSIC)
        val voice = music.copy(id = "v", kind = AudioKind.VOICEOVER)
        return project(videoClip(sourceDurationMs = lengthMs)).copy(audioClips = listOf(music, voice))
    }

    private fun prepare(project: Project, maxSeconds: Int? = null) = runBlocking { job.prepare(project, 10 * minute, maxSeconds) }

    @Test
    fun shortVideo_isOneUploadOfTheWholeSound_withoutMusic() {
        val speech = (prepare(video(4 * minute)) as SpeechPrep.Ready).speech
        assertEquals(listOf("v"), exporter.exported?.audioClips?.map { it.id })
        assertEquals(listOf(SpeechPart(0L, 4 * minute)), speech.parts.map { it.first })
        assertEquals(4 * minute, speech.summary.durationMs)
        assertTrue(speech.summary.partBytes.isEmpty())
    }

    @Test
    fun longVideo_isCutIntoParts_andTheWholeFileIsDeleted() {
        val speech = (prepare(video(25 * minute)) as SpeechPrep.Ready).speech
        assertEquals(3, speech.parts.size)
        assertTrue(speech.parts.all { it.first.durationMs <= 10 * minute && it.second.exists() })
        assertEquals(3, speech.summary.partBytes.size)
        assertEquals(speech.parts.map { it.second.name }.toSet(), dir.listFiles()?.map { it.name }?.toSet())
    }

    @Test
    fun freeMinutes_limitTheParts() {
        val speech = (prepare(video(25 * minute), maxSeconds = 22 * 60) as SpeechPrep.Ready).speech
        assertEquals(2, speech.parts.size)
        assertEquals(25 * minute, speech.durationMs)
        speech.discard()
        assertEquals(SpeechPrep.NoMinutes, prepare(video(25 * minute), maxSeconds = 60))
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun exportFailure_isFailed() {
        exporter.fail = true
        assertEquals(SpeechPrep.Failed, prepare(video(minute)))
    }

    @Test
    fun send_movesWordsToTimelineTime_andDeletesTheParts() {
        val speech = (prepare(video(25 * minute)) as SpeechPrep.Ready).speech
        val heard = runBlocking { job.send(speech, "en", UploadConsent.givenFor(speech.summary)) }
        val starts = speech.parts.map { it.first.startMs }
        assertEquals(starts.map { it + 100L }, heard.words.map { it.startMs })
        assertEquals("eng", heard.language)
        assertEquals(25 * minute, heard.coveredMs)
        assertNull(heard.failure)
        assertEquals(listOf("en", "en", "en"), transcriber.languages)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun failureMidway_keepsTheWordsSoFar() {
        val speech = (prepare(video(25 * minute)) as SpeechPrep.Ready).speech
        transcriber.failOnCall = 2
        val heard = runBlocking { job.send(speech, null, UploadConsent.givenFor(speech.summary)) }
        assertEquals(1, heard.words.size)
        assertEquals(speech.parts[0].first.endMs, heard.coveredMs)
        assertEquals(AiFailure.QuotaUsed(0, null), heard.failure)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun stop_endsBetweenParts() {
        val speech = (prepare(video(25 * minute)) as SpeechPrep.Ready).speech
        val heard = runBlocking { job.send(speech, null, UploadConsent.givenFor(speech.summary), shouldStop = { transcriber.calls >= 1 }) }
        assertTrue(heard.stopped)
        assertEquals(1, transcriber.calls)
        assertFalse(heard.words.isEmpty())
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun oneLine_isJustItsRange() {
        val range = SpeechPart(61_700L, 64_300L)
        val speech = (runBlocking { job.prepareRange(video(4 * minute), range) } as SpeechPrep.Ready).speech
        assertEquals(listOf(range), speech.parts.map { it.first })
        assertEquals(range.durationMs, speech.summary.durationMs)
        assertEquals(listOf(speech.parts.single().second.name), dir.listFiles()?.map { it.name })
        val heard = runBlocking { job.send(speech, "eng", UploadConsent.givenFor(speech.summary)) }
        assertEquals(listOf(61_800L), heard.words.map { it.startMs })
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    private class FakeExporter : VideoExporter {
        lateinit var dir: File
        var exported: Project? = null
        var fail = false
        override fun export(project: Project, settings: ExportSettings, audioOnly: Boolean): Flow<ExportState> = error("not used")
        override fun exportGif(project: Project, gif: GifSettings): Flow<ExportState> = error("not used")
        override fun exportSpeech(project: Project): Flow<ExportState> {
            exported = project
            if (fail) return flowOf(ExportState.Running(null), ExportState.Failed(IllegalStateException("encoder")))
            val file = File(dir, "speech.m4a").apply { writeBytes(ByteArray(1_000)) }
            return flowOf(ExportState.Running(50), ExportState.Success(file))
        }
    }

    /** Silent everywhere; each part's file holds one byte per second of it. */
    private class FakeSpeechAudio : SpeechAudio {
        override suspend fun levels(file: File, windowMs: Long) = FloatArray(0)
        override suspend fun cut(file: File, part: SpeechPart, target: File) = target.writeBytes(ByteArray((part.durationMs / 1_000).toInt()))
    }

    /** Hears one word 100 ms into each part; [failOnCall] (from 1) runs out of minutes. */
    private class FakeTranscriber : Transcriber {
        var calls = 0
        var failOnCall = 0
        val languages = mutableListOf<String?>()
        override suspend fun transcribe(audio: File, mimeType: String, durationMs: Long, language: String?, consent: UploadConsent): AiResult<Transcript> {
            check(consent.spendOn(audio.length())) { "No consent" }
            calls++
            languages += language
            if (calls == failOnCall) return AiResult.Failed(AiFailure.QuotaUsed(0, null))
            return AiResult.Ok(Transcript("eng", durationMs, listOf(TranscriptWord("word$calls.", 100L, 400L))))
        }
    }
}
