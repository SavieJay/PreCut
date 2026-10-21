package com.savie.precut.ui.templates

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.savie.precut.data.InMemoryProjectRepository
import com.savie.precut.data.StoredTemplate
import com.savie.precut.data.TemplateStore
import com.savie.precut.media.ImportResult
import com.savie.precut.media.ImportedAudio
import com.savie.precut.media.ImportedMedia
import com.savie.precut.media.MediaImporter
import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.project
import com.savie.precut.model.template.ProjectTemplate
import com.savie.precut.model.template.TemplateCategory
import com.savie.precut.model.template.TemplateRef
import com.savie.precut.model.template.toTemplate
import com.savie.precut.model.videoClip
import com.savie.precut.ui.navigation.TEMPLATE_ID_ARG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun templateViewModel(id: String, result: ImportResult = media()) =
        TemplateViewModel(SavedStateHandle(mapOf(TEMPLATE_ID_ARG to id)), store, FakeImporter(result), repository, clock)

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
        val vm = TemplatesViewModel(store)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf("t", "p"), vm.state.value.shown?.map { it.template.id })
        assertEquals(listOf(TemplateCategory.TRAVEL, TemplateCategory.BUSINESS), vm.state.value.categories)
        vm.onCategory(TemplateCategory.BUSINESS)
        assertEquals(listOf("p"), vm.state.value.shown?.map { it.template.id })
    }

    private class FakeImporter(private val result: ImportResult) : MediaImporter {
        override suspend fun import(uris: List<String>) = result
        override suspend fun importAudio(uri: String): ImportedAudio? = null
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
