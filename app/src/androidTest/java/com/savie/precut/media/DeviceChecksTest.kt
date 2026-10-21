package com.savie.precut.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.savie.precut.media.export.FrameGrabber
import com.savie.precut.media.frame.LiveFrames
import com.savie.precut.media.audio.AndroidBeatDetector
import com.savie.precut.media.look.LutCache
import com.savie.precut.media.reverse.ClipReverser
import com.savie.precut.media.reverse.ReverseResult
import com.savie.precut.media.sticker.StickerRenderer
import com.savie.precut.media.text.TextFonts
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.AudioClip
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.export.ExportResolution
import com.savie.precut.model.edit.autoCutProject
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Phase 13 device checks: renders test projects through the real export ([ExportManager]) and
 * preview ([FrameGrabber], a `CompositionPlayer`) pipelines and leaves the files for
 * `tools/device_checks.sh`, which pushes the test media first and measures the results with ffmpeg.
 * Skipped when the media isn't there, so a plain `connectedAndroidTest` run is unaffected.
 */
@RunWith(AndroidJUnit4::class)
class DeviceChecksTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var input: File
    private lateinit var output: File
    private lateinit var exporter: ExportManager
    private lateinit var grabber: FrameGrabber
    private lateinit var proxies: ProxyManager
    private lateinit var builder: CompositionBuilder

    @Before
    fun setUp() {
        val root = File(context.filesDir, "device_checks")
        input = File(root, "in")
        output = File(root, "out").apply { mkdirs() }
        assumeTrue("Test media missing: run tools/device_checks.sh", File(input, VIDEO).exists())
        val scope = CoroutineScope(SupervisorJob())
        val thumbnails = ThumbnailLoader(context, Dispatchers.IO)
        val effects = EffectsFactory(
            TextOverlayRenderer(TextFonts(context), StickerRenderer(context)),
            thumbnails,
            LutCache(context, Dispatchers.IO),
            LiveFrames(),
        )
        builder = CompositionBuilder(effects)
        proxies = ProxyManager(context, scope, Dispatchers.IO)
        exporter = ExportManager(context, builder, proxies)
        grabber = FrameGrabber(context, builder)
    }

    /** Text over the second of two clips: its first and last frames in export, and preview frames around its edges. */
    @Test
    fun textTiming() = runBlocking {
        val project = project(
            "text_timing",
            clips = listOf(video("a", 0, 3_000), video("b", 3_000, 6_000)),
            texts = listOf(redBox("t", 4_000, 5_000, x = 0.5f, y = 0.5f)),
        )
        export(project)
        // On frame times (30 fps): the preview shows the first frame at or after the position.
        for (ms in listOf(3_966L, 4_000L, 4_966L, 5_000L)) grab(project, ms)
    }

    /** The two-clip project exported with no frame-rate pass, to tell that pass's effect from the rest. */
    @Test
    @OptIn(UnstableApi::class) // Composition
    fun textTimingNoRateLimit() = runBlocking {
        val project = project(
            "text_timing_free",
            clips = listOf(video("a", 0, 3_000), video("b", 3_000, 6_000)),
            texts = listOf(redBox("t", 4_000, 5_000, x = 0.5f, y = 0.5f)),
        )
        val composition = checkNotNull(builder.build(project, ExportResolution.P720.shortSidePx, output = ExportOutput()))
        val file = File(output, "${project.name}.mp4")
        withTimeout(EXPORT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                runOnMain {
                    Transformer.Builder(context)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) = cont.resume(Unit)
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) =
                                cont.resumeWithException(exportException)
                        })
                        .build()
                        .start(composition, file.absolutePath)
                }
            }
        }
    }

    /** The same text with no cut before it, to tell a cut's effect from the text's own timing. */
    @Test
    fun textTimingOneClip() = runBlocking {
        export(project("text_timing_one", clips = listOf(video("a", 0, 6_000)), texts = listOf(redBox("t", 4_000, 5_000, x = 0.5f, y = 0.5f))))
    }

    /** A turned text box on a 9:16 frame, in export and in preview, at the same moment. */
    @Test
    fun rotatedText() = runBlocking {
        val project = project(
            "rotated_text",
            clips = listOf(video("a", 0, 2_000)),
            texts = listOf(redBox("t", 0, 2_000, x = 0.3f, y = 0.3f, rotation = 30f)),
        )
        export(project)
        grab(project, 1_000)
    }

    /** An audio row with a gap before each of its two clips, over a muted video. */
    @Test
    fun audioGaps() = runBlocking {
        val project = project(
            "audio_gaps",
            clips = listOf(video("a", 0, 6_000).copy(muted = true)),
            audio = listOf(tone("x", start = 1_000, trimEnd = 1_000), tone("y", start = 3_000, trimEnd = 2_000)),
        )
        export(project)
    }

    /** 1 s fades on a 2x clip's own sound. */
    @Test
    fun clipFadesAtSpeed() = runBlocking {
        val clip = video("a", 0, 8_000).copy(speed = 2f, fadeInMs = 1_000, fadeOutMs = 1_000)
        export(project("clip_fades", clips = listOf(clip)))
    }

    /** 1 s fades on a 2x audio clip over a muted video. */
    @Test
    fun audioFadesAtSpeed() = runBlocking {
        val audio = tone("x", start = 0, trimEnd = 8_000).copy(speed = 2f, fadeInMs = 1_000, fadeOutMs = 1_000)
        export(project("audio_fades", clips = listOf(video("a", 0, 4_000).copy(muted = true)), audio = listOf(audio)))
    }

    /** A 50x and a 100x clip followed by a plain one: the export's length and where the plain clip starts. */
    @Test
    fun highSpeed() = runBlocking {
        val clips = listOf(
            video("fast50", 0, 60_000, file = LONG).copy(speed = 50f),
            video("fast100", 0, 60_000, file = LONG).copy(speed = 100f),
            video("plain", 0, 1_000),
        )
        export(project("high_speed", clips = clips))
    }

    /** The preview proxy of a 720p video, copied out for its keyframe spacing. */
    @Test
    fun proxyKeyframes(): Unit = runBlocking {
        val source = Uri.fromFile(File(input, VIDEO)).toString()
        runOnMain { proxies.request(listOf(source)) }
        val ready = withTimeout(PROXY_TIMEOUT_MS) {
            proxies.states.first { it[source] is ProxyState.Ready || it[source] == ProxyState.Failed }[source]
        }
        assertTrue("Proxy failed", ready is ProxyState.Ready)
        val file = File(Uri.parse((ready as ProxyState.Ready).uri).path ?: error("Proxy without a path"))
        file.copyTo(File(output, "proxy.mp4"), overwrite = true)
    }

    /** 3 s of the frame-coded video, reversed: every frame should step one source frame back. */
    @Test
    fun reverseOrder() = runBlocking { reverse("reverse", VIDEO) }

    /** The same from a copy flagged to show rotated: the reversed clip must keep showing that way. */
    @Test
    fun reverseKeepsRotation() = runBlocking { reverse("reverse_rotated", ROTATED) }

    private suspend fun reverse(name: String, file: String) {
        val reverser = ClipReverser(context, Dispatchers.IO, proxies)
        val result = withTimeout(EXPORT_TIMEOUT_MS) { reverser.reverse(Uri.fromFile(File(input, file)).toString(), 0, 3_000, 720) {} }
        assertTrue("Reverse of $file: $result", result is ReverseResult.Done)
        val reversed = File(checkNotNull(Uri.parse((result as ReverseResult.Done).uri).path) { "No path in ${result.uri}" })
        reversed.copyTo(File(output, "$name.mp4"), overwrite = true)
        // Not left in the app's reversed-clip cache, so the next run reverses again.
        reversed.delete()
    }

    /** Beats of a 120 BPM click track (clicks from 0.25 s), as the app finds them on this phone. */
    @Test
    fun beatDetection() = runBlocking {
        val result = checkNotNull(AndroidBeatDetector(context, Dispatchers.IO).detect(Uri.fromFile(File(input, CLICKS)).toString())) { "Unreadable" }
        File(output, "beats.json").writeText(JSONObject().put("beatsMs", JSONArray(result.beatsMs)).put("bpm", result.bpm.toDouble()).toString())
    }

    /** AutoCut of four copies of the frame-coded video to the click track: every cut should sit on a beat. */
    @Test
    fun autoCut() = runBlocking {
        val clicks = Uri.fromFile(File(input, CLICKS)).toString()
        val beats = checkNotNull(AndroidBeatDetector(context, Dispatchers.IO).detect(clicks)) { "Unreadable" }.beatsMs
        val music = AudioClip(id = "m", uri = clicks, sourceDurationMs = 20_000, trimEndMs = 20_000)
        val project = autoCutProject(project("autocut", clips = (1..4).map { video("c$it", 0, 10_000) }), music, beats)
        export(project)
        File(output, "autocut_beats.json").writeText(JSONObject().put("beatsMs", JSONArray(project.audioClips.single().beatsMs)).toString())
    }

    private suspend fun export(project: Project) {
        val result = withTimeout(EXPORT_TIMEOUT_MS) {
            exporter.export(project, ExportSettings(resolution = ExportResolution.P720)).last()
        }
        assertTrue("Export of ${project.name} failed: $result", result is ExportState.Success)
        val file = (result as ExportState.Success).file
        file.copyTo(File(output, "${project.name}.mp4"), overwrite = true)
        file.delete()
        File(output, "${project.name}.json").writeText(
            JSONObject().put("totalDurationMs", project.totalDurationMs()).toString(),
        )
    }

    private suspend fun grab(project: Project, positionMs: Long) {
        val frame = grabber.grab(project, positionMs, ExportResolution.P720.shortSidePx)
        assertNotNull("No preview frame for ${project.name} at $positionMs", frame)
        File(output, "${project.name}_preview_$positionMs.png").outputStream().use {
            frame?.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun runOnMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun project(name: String, clips: List<Clip>, texts: List<TextOverlay> = emptyList(), audio: List<AudioClip> = emptyList()) =
        Project(
            id = name, name = name, aspectRatio = AspectRatio.RATIO_9_16,
            clips = clips, textOverlays = texts, audioClips = audio, createdAt = 0, updatedAt = 0,
        )

    private fun video(id: String, trimStart: Long, trimEnd: Long, file: String = VIDEO) = Clip(
        id = id,
        uri = Uri.fromFile(File(input, file)).toString(),
        type = ClipType.VIDEO,
        sourceDurationMs = if (file == LONG) LONG_MS else VIDEO_MS,
        trimStartMs = trimStart,
        trimEndMs = trimEnd,
        sourceWidth = 1280,
        sourceHeight = 720,
    )

    private fun tone(id: String, start: Long, trimEnd: Long) = AudioClip(
        id = id,
        uri = Uri.fromFile(File(input, TONE)).toString(),
        sourceDurationMs = VIDEO_MS,
        timelineStartMs = start,
        trimEndMs = trimEnd,
    )

    /** Solid red (red text on red), so the box is easy to find in a frame. */
    private fun redBox(id: String, start: Long, end: Long, x: Float, y: Float, rotation: Float = 0f) = TextOverlay(
        id = id, text = "CHECK", startMs = start, endMs = end, xNorm = x, yNorm = y, textSizeSp = 40f,
        colorArgb = RED, backgroundArgb = RED, rotationDegrees = rotation,
    )

    private companion object {
        const val VIDEO = "video.mp4" // 1280x720, 30 fps, 10 s, grey with a 440 Hz tone
        const val LONG = "long.mp4" // 1280x720, 30 fps, 60 s, blue, silent
        const val TONE = "tone.m4a" // 10 s, 1 kHz
        const val ROTATED = "rotated.mp4" // video.mp4 flagged to display turned 90°
        const val CLICKS = "clicks.m4a" // 20 s, a 1 kHz click every 0.5 s from 0.25 s (120 BPM)
        const val VIDEO_MS = 10_000L
        const val LONG_MS = 60_000L
        const val RED = 0xFFFF0000.toInt()
        const val EXPORT_TIMEOUT_MS = 5 * 60_000L
        const val PROXY_TIMEOUT_MS = 5 * 60_000L
    }
}
