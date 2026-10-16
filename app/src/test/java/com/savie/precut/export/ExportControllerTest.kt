package com.savie.precut.export

import app.cash.turbine.test
import com.savie.precut.data.AppStorage
import com.savie.precut.data.InMemoryProjectRepository
import com.savie.precut.media.ExportState
import com.savie.precut.media.MediaSaver
import com.savie.precut.media.SavedMedia
import com.savie.precut.media.VideoExporter
import com.savie.precut.model.Project
import com.savie.precut.model.export.ExportResolution
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ExportControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = InMemoryProjectRepository()
    private val exportStates = MutableSharedFlow<ExportState>()
    private val exporter = FakeExporter(exportStates)
    private val saver = FakeSaver()
    private val storage = FakeStorage()
    private var serviceStarts = 0
    private val clock = Clock.fixed(Instant.parse("2026-10-12T10:20:30Z"), ZoneOffset.UTC)
    private val settings = ExportSettings(resolution = ExportResolution.P720)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun controller() =
        ExportController(repository, exporter, saver, storage, clock, { serviceStarts++ }, CoroutineScope(dispatcher))

    private fun job(kind: ExportKind = ExportKind.VIDEO) = ExportJob("p", "", 10_000L, kind, settings)

    @Test
    fun successfulExport_runsInTheService_reportsProgress_thenSaves() = runTest(dispatcher) {
        repository.save(project(videoClip("a")))
        val controller = controller()

        controller.state.test {
            assertEquals(ExportJobState.Idle, awaitItem())
            assertTrue(controller.start("p", settings, ExportKind.VIDEO))
            assertEquals(ExportJobState.Running(job(), null), awaitItem())
            assertEquals(1, serviceStarts)
            assertEquals(settings, exporter.lastSettings)

            exportStates.emit(ExportState.Running(40))
            assertEquals(ExportJobState.Running(job(), 40), awaitItem())

            exportStates.emit(ExportState.Success(File("out.mp4")))
            assertEquals(ExportJobState.Saving(job()), awaitItem())
            assertEquals(ExportJobState.Done(job(), "content://saved", 0L), awaitItem())
        }
        // The name uses local time, so only its shape is checked.
        assertTrue(saver.savedName.orEmpty().matches(Regex("PreCut_20261012_\\d{6}\\.mp4")))
        assertEquals(SavedMedia.VIDEO, saver.savedAs)
    }

    @Test
    fun audioOnly_exportsSoundAndSavesToMusic() = runTest(dispatcher) {
        repository.save(project(videoClip("a")))
        val controller = controller()
        controller.start("p", settings, ExportKind.AUDIO)
        dispatcher.scheduler.runCurrent()
        assertTrue(exporter.lastAudioOnly)
        exportStates.emit(ExportState.Success(File("out.m4a")))
        dispatcher.scheduler.runCurrent()
        assertEquals(SavedMedia.AUDIO, saver.savedAs)
        assertTrue(saver.savedName.orEmpty().endsWith(".m4a"))
    }

    @Test
    fun encoderFailure_isReportedAsSuch_andRetryStartsAgain() = runTest(dispatcher) {
        repository.save(project(videoClip("a")))
        val controller = controller()

        controller.state.test {
            skipItems(1)
            controller.start("p", settings, ExportKind.VIDEO)
            skipItems(1)
            exportStates.emit(ExportState.Failed(RuntimeException("codec"), encoder = true))
            assertEquals(ExportJobState.Failed(job(), ExportFailure.ENCODER, "codec"), awaitItem())

            controller.start("p", settings, ExportKind.VIDEO)
            assertEquals(ExportJobState.Running(job(), null), awaitItem())
        }
        assertEquals(2, exporter.starts)
    }

    @Test
    fun notEnoughStorage_failsBeforeExporting() = runTest(dispatcher) {
        repository.save(project(videoClip("a")))
        storage.free = 10L * 1024 * 1024
        val controller = controller()
        controller.start("p", settings, ExportKind.VIDEO)
        dispatcher.scheduler.runCurrent()
        val failed = controller.state.value as ExportJobState.Failed
        assertEquals(ExportFailure.LOW_STORAGE, failed.reason)
        assertEquals(storage.free, failed.freeBytes)
        assertTrue(failed.neededBytes > failed.freeBytes)
        assertEquals(0, exporter.starts)
        assertEquals(0, serviceStarts)
    }

    @Test
    fun saveFailure_isReported() = runTest(dispatcher) {
        repository.save(project(videoClip("a")))
        saver.result = Result.failure(java.io.IOException("disk full"))
        val controller = controller()

        controller.state.test {
            skipItems(1)
            controller.start("p", settings, ExportKind.VIDEO)
            skipItems(1)
            exportStates.emit(ExportState.Success(File("out.mp4")))
            assertEquals(ExportJobState.Saving(job()), awaitItem())
            assertEquals(ExportJobState.Failed(job(), ExportFailure.SAVE_FAILED, "disk full"), awaitItem())
        }
    }

    @Test
    fun missingProject_failsWithoutExporting() = runTest(dispatcher) {
        val controller = controller()
        controller.start("missing", settings, ExportKind.VIDEO)
        dispatcher.scheduler.runCurrent()
        assertEquals(ExportJobState.Failed(null, ExportFailure.PROJECT_NOT_FOUND), controller.state.value)
        assertEquals(0, exporter.starts)
    }

    @Test
    fun oneExportAtATime_andCancelStopsIt() = runTest(dispatcher) {
        repository.save(project(videoClip("a")))
        val controller = controller()

        assertTrue(controller.start("p", settings, ExportKind.VIDEO))
        dispatcher.scheduler.runCurrent()
        assertFalse(controller.start("p", settings, ExportKind.VIDEO))
        controller.cancel()
        dispatcher.scheduler.runCurrent()

        assertTrue(exporter.cancelled)
        assertEquals(ExportJobState.Idle, controller.state.value)
    }

    private class FakeExporter(private val states: Flow<ExportState>) : VideoExporter {
        var starts = 0
        var cancelled = false
        var lastSettings: ExportSettings? = null
        var lastAudioOnly = false
        override fun export(project: Project, settings: ExportSettings, audioOnly: Boolean): Flow<ExportState> {
            starts++
            lastSettings = settings
            lastAudioOnly = audioOnly
            // Like ExportManager, the flow completes after a terminal state.
            return states
                .transformWhile { emit(it); it is ExportState.Running }
                .onCompletion { if (it != null) cancelled = true }
        }
    }

    private class FakeSaver : MediaSaver {
        var result: Result<String?> = Result.success("content://saved")
        var savedName: String? = null
        var savedAs: SavedMedia? = null
        override suspend fun save(file: File, displayName: String, media: SavedMedia): Result<String?> {
            savedName = displayName
            savedAs = media
            return result
        }
    }

    private class FakeStorage : AppStorage {
        var free = 64L * 1024 * 1024 * 1024
        override fun freeBytes(): Long = free
        override suspend fun cacheBytes(): Long = 0L
        override suspend fun clearCache() = Unit
    }
}
