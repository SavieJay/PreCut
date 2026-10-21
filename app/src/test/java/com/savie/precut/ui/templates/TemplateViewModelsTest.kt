package com.savie.precut.ui.templates

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.savie.precut.data.InMemoryProjectRepository
import com.savie.precut.data.StoredTemplate
import com.savie.precut.data.TemplateHistory
import com.savie.precut.data.TemplateStore
import com.savie.precut.media.ImportResult
import com.savie.precut.media.ImportedAudio
import com.savie.precut.media.ImportedMedia
import com.savie.precut.media.MediaImporter
import com.savie.precut.media.audio.BeatDetector
import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.audio.BeatResult
import com.savie.precut.model.project
import com.savie.precut.model.template.ProjectTemplate
import com.savie.precut.model.template.TemplateCategory
import com.savie.precut.model.template.TemplateRef
import com.savie.precut.model.template.toTemplate
import com.savie.precut.model.videoClip
import com.savie.precut.ui.navigation.TEMPLATE_ID_ARG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class TemplateViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = InMemoryProjectRepository()
    private val clock = Clock.fixed(Instant.ofEpochMilli(77L), ZoneOffset.UTC)

    private val travel = project(videoClip("a"), videoClip("b")).toTemplate("t", "Trip", TemplateCategory.TRAVEL, setOf("a", "b"), emptySet(), nowMs = 2)
    private val promo = project(videoClip("a")).toTemplate("p", "Promo", TemplateCategory.BUSINESS, setOf("a"), emptySet(), nowMs = 1)
    private val store = FakeStore(mutableMapOf("t" to travel, "p" to promo))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun media(vararg uris: String) = ImportResult(uris.map { ImportedMedia(it, MediaInfo(ClipType.VIDEO, 30_000L, 1920, 1080, 0)) }, failedCount = 0)

    private val history = FakeHistory()
    private val beats = FakeBeats()

    private fun templateViewModel(id: String, result: ImportResult = media(), song: ImportedAudio? = null) =
        TemplateViewModel(SavedStateHandle(mapOf(TEMPLATE_ID_ARG to id)), store, FakeImporter(result, song), repository, clock, history, beats)

    @Test
    fun usingATemplate_makesANormalProjectFromThePicks() = runTest(dispatcher) {
        val vm = templateViewModel("t", media("content://1", "content://2", "content://3"))
        vm.events.test {
            vm.onUse(listOf("content://1", "content://2", "content://3"))
            assertEquals(TemplateEvent.ExtraPicksIgnored(1), awaitItem())
            val project = requireNotNull(repository.load((awaitItem() as TemplateEvent.OpenEditor).projectId))
            assertEquals(listOf("content://1", "content://2"), project.clips.map { it.uri })
            assertEquals(TemplateRef("t", "Trip"), project.template)
            assertEquals(77L, project.createdAt)
        }
        assertEquals(false, vm.state.value.busy)
        assertEquals(listOf("t"), history.recent.value)
    }

    @Test
    fun usingWithASong_retimesTheClipsToItsBeats() = runTest(dispatcher) {
        // Trip has 10 s slots; at 120 BPM that's 20 beats a cut, capped at 8 (4 s). The first kept
        // beat (0.5 s) is under half a step from the start, so the first clip runs to 4.5 s.
        beats.result = BeatResult((500L..60_000L step 500L).toList(), 120f, List(120) { 1f })
        val vm = templateViewModel("t", media("content://1", "content://2"), song = ImportedAudio("content://song", 60_000, "Song"))
        vm.events.test {
            vm.onUse(listOf("content://1", "content://2"), song = "content://song")
            val project = requireNotNull(repository.load((awaitItem() as TemplateEvent.OpenEditor).projectId))
            assertEquals(listOf(4_500L, 4_000L), project.clips.map { it.trimEndMs - it.trimStartMs })
            assertEquals("content://song", project.audioClips.single().uri)
        }
    }

    @Test
    fun anUnreadableSong_makesNothing() = runTest(dispatcher) {
        val vm = templateViewModel("t", media("content://1"))
        vm.events.test {
            vm.onUse(listOf("content://1"), song = "content://bad")
            assertEquals(TemplateEvent.SongUnreadable, awaitItem())
        }
        assertTrue(repository.loadAll().isEmpty())
    }

    @Test
    fun theStar_togglesTheFavourite() = runTest(dispatcher) {
        val vm = templateViewModel("t")
        advanceUntilIdle()
        vm.onToggleFavourite()
        advanceUntilIdle()
        assertTrue(vm.state.value.favourite)
    }

    @Test
    fun aMissingTemplate_saysSo() = runTest(dispatcher) {
        val vm = templateViewModel("gone")
        advanceUntilIdle()
        assertTrue(vm.state.value.missing)
    }

    @Test
    fun deleting_removesItAndGoesBack() = runTest(dispatcher) {
        val vm = templateViewModel("p")
        vm.events.test {
            vm.onDelete()
            assertEquals(TemplateEvent.Deleted, awaitItem())
        }
        assertEquals(setOf("t"), store.templates.keys)
    }

    @Test
    fun theList_isNewestFirst_andFiltersByCategory() = runTest(dispatcher) {
        val vm = TemplatesViewModel(store, history)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf("t", "p"), vm.state.value.shown?.map { it.template.id })
        assertEquals(
            listOf(TemplateFilter.All, TemplateFilter.Category(TemplateCategory.TRAVEL), TemplateFilter.Category(TemplateCategory.BUSINESS)),
            vm.state.value.filters,
        )
        vm.onFilter(TemplateFilter.Category(TemplateCategory.BUSINESS))
        assertEquals(listOf("p"), vm.state.value.shown?.map { it.template.id })
        history.toggleFavourite("t")
        advanceUntilIdle()
        vm.onFilter(TemplateFilter.Favourites)
        assertEquals(listOf("t"), vm.state.value.shown?.map { it.template.id })
    }

    private class FakeImporter(private val result: ImportResult, private val song: ImportedAudio?) : MediaImporter {
        override suspend fun import(uris: List<String>) = result
        override suspend fun importAudio(uri: String): ImportedAudio? = song
    }

    private class FakeHistory : TemplateHistory {
        override val favourites = MutableStateFlow(emptySet<String>())
        override val recent = MutableStateFlow(emptyList<String>())
        override fun toggleFavourite(templateId: String) = favourites.update { if (templateId in it) it - templateId else it + templateId }
        override fun used(templateId: String) = recent.update { listOf(templateId) + (it - templateId) }
    }

    private class FakeBeats : BeatDetector {
        var result: BeatResult? = BeatResult.NONE
        override suspend fun detect(uri: String) = result
    }

    private class FakeStore(val templates: MutableMap<String, ProjectTemplate>) : TemplateStore {
        override suspend fun list() = templates.values.sortedByDescending { it.createdAt }.map { StoredTemplate(it, null, null) }
        override suspend fun get(id: String) = templates[id]?.let { StoredTemplate(it, null, null) }
        override suspend fun save(template: ProjectTemplate) = StoredTemplate(template, null, null).also { templates[template.id] = template }
        override suspend fun attach(id: String, preview: File?, thumbnail: Bitmap?) = Unit
        override suspend fun delete(id: String) {
            templates.remove(id)
        }
        override suspend fun forUse(id: String) = templates[id]
    }
}
