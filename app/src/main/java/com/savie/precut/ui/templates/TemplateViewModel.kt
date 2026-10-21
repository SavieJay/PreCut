package com.savie.precut.ui.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.data.ProjectRepository
import com.savie.precut.data.StoredTemplate
import com.savie.precut.data.TemplateStore
import com.savie.precut.media.MediaImporter
import com.savie.precut.model.template.TemplatePick
import com.savie.precut.model.template.fill
import com.savie.precut.ui.navigation.requireTemplateId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** One template: [template] null while loading; [busy] while a project is being made from it. */
data class TemplateState(val template: StoredTemplate? = null, val missing: Boolean = false, val busy: Boolean = false)

sealed interface TemplateEvent {
    data class OpenEditor(val projectId: String) : TemplateEvent
    data object NothingImported : TemplateEvent
    /** Picked files that couldn't be read. */
    data class SomeFilesSkipped(val count: Int) : TemplateEvent
    /** More picks than slots: the extra ones weren't used. */
    data class ExtraPicksIgnored(val count: Int) : TemplateEvent
    /** Videos too short for their slot even slowed to half speed. */
    data class SomeClipsShort(val count: Int) : TemplateEvent
    data object Deleted : TemplateEvent
}

/** A template's page: preview, Use template (picked media into a new, normal project), Delete. */
@HiltViewModel
class TemplateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: TemplateStore,
    private val importer: MediaImporter,
    private val repository: ProjectRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id = savedStateHandle.requireTemplateId()

    private val _state = MutableStateFlow(TemplateState())
    val state: StateFlow<TemplateState> = _state.asStateFlow()

    private val _events = Channel<TemplateEvent>(Channel.BUFFERED)
    val events: Flow<TemplateEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val template = store.get(id)
            _state.update { it.copy(template = template, missing = template == null) }
        }
    }

    /** Fills the template with [uris] (in pick order) and opens the new project. */
    fun onUse(uris: List<String>) {
        if (uris.isEmpty() || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val result = importer.import(uris)
                if (result.imported.isEmpty()) {
                    _events.send(TemplateEvent.NothingImported)
                    return@launch
                }
                val template = store.forUse(id) ?: run {
                    _state.update { it.copy(missing = true) }
                    return@launch
                }
                val filled = template.fill(result.imported.map { TemplatePick(it.uri, it.info) }, UUID.randomUUID().toString(), clock.millis())
                repository.save(filled.project)
                if (result.failedCount > 0) _events.send(TemplateEvent.SomeFilesSkipped(result.failedCount))
                if (filled.ignoredPicks > 0) _events.send(TemplateEvent.ExtraPicksIgnored(filled.ignoredPicks))
                if (filled.shorter > 0) _events.send(TemplateEvent.SomeClipsShort(filled.shorter))
                _events.send(TemplateEvent.OpenEditor(filled.project.id))
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun onDelete() {
        viewModelScope.launch {
            store.delete(id)
            _events.send(TemplateEvent.Deleted)
        }
    }
}
