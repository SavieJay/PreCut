package com.savie.precut.ui.home

import app.cash.turbine.test
import com.savie.precut.data.InMemoryProjectRepository
import com.savie.precut.media.ImportResult
import com.savie.precut.media.ImportedMedia
import com.savie.precut.media.MediaImporter
import com.savie.precut.model.ClipType
import com.savie.precut.model.DEFAULT_IMAGE_DURATION_MS
import com.savie.precut.model.MediaInfo
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

    private val dispatcher = StandardTestDispatcher()
    private val repository = InMemoryProjectRepository()
    private val clock = Clock.fixed(Instant.ofEpochMilli(42L), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(result: ImportResult) = HomeViewModel(FakeImporter(result), repository, clock)

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

    private class FakeImporter(private val result: ImportResult) : MediaImporter {
        override suspend fun import(uris: List<String>) = result
    }
}
