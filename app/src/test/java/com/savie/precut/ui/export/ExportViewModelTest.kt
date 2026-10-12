package com.savie.precut.ui.export

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.savie.precut.data.InMemoryProjectRepository
import com.savie.precut.media.ExportState
import com.savie.precut.media.MediaSaver
import com.savie.precut.media.VideoExporter
import com.savie.precut.model.Project
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import com.savie.precut.ui.navigation.PROJECT_ID_ARG
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = InMemoryProjectRepository()
    private val exportStates = MutableSharedFlow<ExportState>()
    private val exporter = FakeExporter(exportStates)
    private val saver = FakeSaver()
    private val clock = Clock.fixed(Instant.parse("2026-10-12T10:20:30Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(projectId: String = "p") =
        ExportViewModel(SavedStateHandle(mapOf(PROJECT_ID_ARG to projectId)), repository, exporter, saver, clock)

    @Test
    fun successfulExport_reportsProgress_thenSavesToGallery() = runTest(dispatcher) {
        repository.save(project(videoClip("a")))
        val vm = viewModel()

        vm.uiState.test {
            assertEquals(ExportUiState.Idle, awaitItem())
            vm.start()
            assertEquals(ExportUiState.Exporting(null), awaitItem())

            exportStates.emit(ExportState.Running(40))
            assertEquals(ExportUiState.Exporting(40), awaitItem())

            exportStates.emit(ExportState.Success(File("out.mp4")))
            assertEquals(ExportUiState.Saving, awaitItem())
            assertEquals(ExportUiState.Done("content://saved"), awaitItem())
        }
        assertTrue(saver.savedName.orEmpty().startsWith("PreCut_"))
    }

    @Test
    fun exportError_isReported_andRetryStartsAgain() = runTest(dispatcher) {
        repository.save(project(videoClip("a")))
        val vm = viewModel()

        vm.uiState.test {
            skipItems(1)
            vm.start()
            skipItems(1)
            exportStates.emit(ExportState.Failed(RuntimeException("codec")))
            assertEquals(ExportUiState.Failed(ExportFailure.EXPORT_FAILED, "codec"), awaitItem())

            vm.start()
            assertEquals(ExportUiState.Exporting(null), awaitItem())
        }
        assertEquals(2, exporter.starts)
    }

    @Test
    fun saveFailure_isReported() = runTest(dispatcher) {
        repository.save(project(videoClip("a")))
        saver.result = Result.failure(java.io.IOException("disk full"))
        val vm = viewModel()

        vm.uiState.test {
            skipItems(1)
            vm.start()
            skipItems(1)
            exportStates.emit(ExportState.Success(File("out.mp4")))
            assertEquals(ExportUiState.Saving, awaitItem())
            assertEquals(ExportUiState.Failed(ExportFailure.SAVE_FAILED, "disk full"), awaitItem())
        }
    }

    @Test
    fun missingProject_failsWithoutExporting() = runTest(dispatcher) {
        val vm = viewModel(projectId = "missing")

        vm.uiState.test {
            skipItems(1)
            vm.start()
            assertEquals(ExportUiState.Failed(ExportFailure.PROJECT_NOT_FOUND), awaitItem())
        }
        assertEquals(0, exporter.starts)
    }

    @Test
    fun cancel_stopsCollectingTheExport() = runTest(dispatcher) {
        repository.save(project(videoClip("a")))
        val vm = viewModel()

        vm.start()
        dispatcher.scheduler.runCurrent()
        vm.cancel()
        dispatcher.scheduler.runCurrent()

        assertTrue(exporter.cancelled)
    }

    private class FakeExporter(private val states: Flow<ExportState>) : VideoExporter {
        var starts = 0
        var cancelled = false
        override fun export(project: Project): Flow<ExportState> {
            starts++
            // Like ExportManager, the flow completes after a terminal state.
            return states
                .transformWhile { emit(it); it is ExportState.Running }
                .onCompletion { if (it != null) cancelled = true }
        }
    }

    private class FakeSaver : MediaSaver {
        var result: Result<String?> = Result.success("content://saved")
        var savedName: String? = null
        override suspend fun saveVideo(file: File, displayName: String): Result<String?> {
            savedName = displayName
            return result
        }
    }
}
