package com.savie.precut.ui.home

import app.cash.turbine.test
import com.savie.precut.data.InMemoryProjectRepository
import com.savie.precut.data.ShareInbox
import com.savie.precut.data.UnusedMediaCleaner
import com.savie.precut.media.Capture
import com.savie.precut.media.CaptureStore
import com.savie.precut.media.ImportResult
import com.savie.precut.media.ImportedAudio
import com.savie.precut.media.ImportedMedia
import com.savie.precut.media.MediaImporter
import com.savie.precut.media.SharedMediaStore
import com.savie.precut.model.ClipType
import com.savie.precut.model.DEFAULT_IMAGE_DURATION_MS
import com.savie.precut.model.MediaInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val settings = com.savie.precut.data.InMemoryAppSettings()


    private val dispatcher = StandardTestDispatcher()
    private val repository = InMemoryProjectRepository()
    private val clock = Clock.fixed(Instant.ofEpochMilli(42L), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val shareInbox = ShareInbox()
    private val captures = FakeCaptures()
    private var importer = FakeImporter(ImportResult(emptyList(), 0))

    private fun viewModel(result: ImportResult, cleaner: UnusedMediaCleaner = NoOpCleaner): HomeViewModel {
        importer = FakeImporter(result)
        return HomeViewModel(importer, repository, clock, cleaner, captures, shareInbox, FakeSharedMedia, settings)
    }

    @Test
    fun picking_media_createsProjectInPickOrder_andOpensEditor() = runTest(dispatcher) {
        val vm = viewModel(
            ImportResult(
                imported = listOf(
                    ImportedMedia("content://v1", MediaInfo(ClipType.VIDEO, 5_000L, 1920, 1080, 0)),
                    ImportedMedia("content://i1", MediaInfo(ClipType.IMAGE, 0L, 800, 600, 0)),
                ),
                failedCount = 0,
            ),
        )

        vm.events.test {
            vm.onMediaPicked(listOf("content://v1", "content://i1"))
            val event = awaitItem() as HomeEvent.OpenEditor

            val project = requireNotNull(repository.load(event.projectId))
            assertEquals(listOf("content://v1", "content://i1"), project.clips.map { it.uri })
            assertEquals(5_000L, project.clips[0].trimEndMs)
            assertEquals(DEFAULT_IMAGE_DURATION_MS, project.clips[1].trimEndMs)
            assertEquals(42L, project.createdAt)
        }
        assertFalse(vm.uiState.value.isImporting)
    }

    @Test
    fun skipped_files_areReported_beforeOpeningEditor() = runTest(dispatcher) {
        val vm = viewModel(
            ImportResult(
                imported = listOf(ImportedMedia("content://v1", MediaInfo(ClipType.VIDEO, 5_000L, 1, 1, 0))),
                failedCount = 2,
            ),
        )

        vm.events.test {
            vm.onMediaPicked(listOf("content://v1", "content://bad1", "content://bad2"))
            assertEquals(HomeEvent.SomeFilesSkipped(2), awaitItem())
            assertTrue(awaitItem() is HomeEvent.OpenEditor)
        }
    }

    @Test
    fun nothing_imported_emitsError_andCreatesNoProject() = runTest(dispatcher) {
        val vm = viewModel(ImportResult(imported = emptyList(), failedCount = 1))

        vm.events.test {
            vm.onMediaPicked(listOf("content://bad"))
            assertEquals(HomeEvent.NothingImported, awaitItem())
        }
        assertFalse(vm.uiState.value.isImporting)
    }

    @Test
    fun empty_pick_isIgnored() = runTest(dispatcher) {
        val vm = viewModel(ImportResult(emptyList(), 0))

        vm.events.test {
            vm.onMediaPicked(emptyList())
            expectNoEvents()
        }
        assertFalse(vm.uiState.value.isImporting)
    }

    private object NoOpCleaner : UnusedMediaCleaner {
        override suspend fun sweep() = Unit
    }

    @Test
    fun drafts_areListed_andCanBeRenamedDuplicatedAndDeleted() = runTest(dispatcher) {
        repository.save(com.savie.precut.model.project().copy(id = "p1", name = "One", updatedAt = 5L))
        val vm = viewModel(ImportResult(emptyList(), 0))
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("One"), vm.uiState.value.projects?.map { it.name })

        vm.onRename("p1", "  Trip ")
        vm.onDuplicate("p1")
        dispatcher.scheduler.runCurrent()
        assertEquals(setOf("Trip"), vm.uiState.value.projects?.map { it.name }?.toSet())
        assertEquals(2, vm.uiState.value.projects?.size)

        vm.onDelete("p1")
        dispatcher.scheduler.runCurrent()
        assertEquals(1, vm.uiState.value.projects?.size)
    }

    @Test
    fun sharedMedia_isCopiedIntoAppStorage_thenOpensAsNewProject() = runTest(dispatcher) {
        val video = ImportedMedia("file:///imports/a", MediaInfo(ClipType.VIDEO, 5_000L, 1920, 1080, 0))
        shareInbox.offer(listOf("content://share/a", "content://share/bad"))
        val vm = viewModel(ImportResult(listOf(video), 0))
        vm.events.test {
            dispatcher.scheduler.advanceUntilIdle()
            // The unreadable share counts as skipped.
            assertEquals(HomeEvent.SomeFilesSkipped(1), awaitItem())
            val open = awaitItem() as HomeEvent.OpenEditor
            assertEquals(listOf("file:///imports/a"), repository.load(open.projectId)?.clips?.map { it.uri })
        }
        assertEquals(listOf("file:///imports/a"), importer.received)
        assertTrue(shareInbox.pending.value.isEmpty())
    }

    @Test
    fun sharedMedia_waitsForTheStartupSweep_soItsCopyIsntDeleted() = runTest(dispatcher) {
        val sweepDone = CompletableDeferred<Unit>()
        val cleaner = object : UnusedMediaCleaner {
            override suspend fun sweep() = sweepDone.await()
        }
        val video = ImportedMedia("file:///imports/a", MediaInfo(ClipType.VIDEO, 5_000L, 1920, 1080, 0))
        shareInbox.offer(listOf("content://share/a"))
        val vm = viewModel(ImportResult(listOf(video), 0), cleaner)
        vm.events.test {
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(importer.received.isEmpty())
            sweepDone.complete(Unit)
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(awaitItem() is HomeEvent.OpenEditor)
        }
        assertEquals(listOf("file:///imports/a"), importer.received)
    }

    @Test
    fun camera_success_createsProject_cancelDiscardsTheFile() = runTest(dispatcher) {
        val photo = ImportedMedia("file:///captures/1.jpg", MediaInfo(ClipType.IMAGE, 0L, 10, 10, 0))
        val vm = viewModel(ImportResult(listOf(photo), 0))
        vm.events.test {
            vm.onCameraRequested(video = false)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(HomeEvent.LaunchCamera("content://captures/1.jpg", video = false), awaitItem())
            vm.onCaptureResult(success = true)
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(awaitItem() is HomeEvent.OpenEditor)

            vm.onCameraRequested(video = true)
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem()
            vm.onCaptureResult(success = false)
            expectNoEvents()
        }
        assertEquals(listOf("file:///captures/2.mp4"), captures.discarded)
    }

    private class FakeImporter(private val result: ImportResult) : MediaImporter {
        var received: List<String> = emptyList()
        override suspend fun import(uris: List<String>): ImportResult {
            received = uris
            return result
        }
        override suspend fun importAudio(uri: String): ImportedAudio? = null
    }

    private class FakeCaptures : CaptureStore {
        private var count = 0
        val discarded = mutableListOf<String>()
        override fun newCapture(video: Boolean): Capture {
            val name = "${++count}.${if (video) "mp4" else "jpg"}"
            return Capture("file:///captures/$name", "content://captures/$name")
        }
        override fun discard(capture: Capture) {
            discarded += capture.fileUri
        }
    }

    /** "Copies" readable shares to file Uris; anything named "bad" fails. */
    private object FakeSharedMedia : SharedMediaStore {
        override suspend fun copyIn(uris: List<String>) =
            uris.filterNot { it.endsWith("bad") }.map { "file:///imports/" + it.substringAfterLast('/') }
    }
}
