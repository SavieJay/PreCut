package com.savie.precut.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.data.StoredTemplate
import com.savie.precut.data.TemplateHistory
import com.savie.precut.data.TemplateStore
import com.savie.precut.model.template.TemplateCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Templates tab shows: everything, starred ones, recently used ones, or one category. */
sealed interface TemplateFilter {
    data object All : TemplateFilter
    data object Favourites : TemplateFilter
    data object Recent : TemplateFilter
    data class Category(val category: TemplateCategory) : TemplateFilter
}

/** The Templates tab: [templates] null while loading; [shown] per [filter]. */
data class TemplatesState(
    val templates: List<StoredTemplate>? = null,
    val filter: TemplateFilter = TemplateFilter.All,
    val favourites: Set<String> = emptySet(),
    val recent: List<String> = emptyList(),
) {
    val shown: List<StoredTemplate>? get() = templates?.let { all ->
        when (filter) {
            TemplateFilter.All -> all
            TemplateFilter.Favourites -> all.filter { it.template.id in favourites }
            TemplateFilter.Recent -> recent.mapNotNull { id -> all.firstOrNull { it.template.id == id } }
            is TemplateFilter.Category -> all.filter { it.template.category == filter.category }
        }
    }

    /** Only filters that show something, so a chip never leads to an empty grid. */
    val filters: List<TemplateFilter> get() {
        val ids = templates.orEmpty().map { it.template.id }.toSet()
        return listOf(TemplateFilter.All) +
            listOfNotNull(TemplateFilter.Favourites.takeIf { favourites.any(ids::contains) }, TemplateFilter.Recent.takeIf { recent.any(ids::contains) }) +
            templates.orEmpty().map { it.template.category }.distinct().sorted().map(TemplateFilter::Category)
    }
}

@HiltViewModel
class TemplatesViewModel @Inject constructor(private val store: TemplateStore, history: TemplateHistory) : ViewModel() {
    private val _state = MutableStateFlow(TemplatesState())
    val state: StateFlow<TemplatesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(history.favourites, history.recent, ::Pair).collect { (favourites, recent) ->
                _state.update { it.copy(favourites = favourites, recent = recent).keptFilter() }
            }
        }
    }

    /** Reads the templates again (one may have been saved or deleted since). */
    fun refresh() {
        viewModelScope.launch {
            val templates = store.list()
            _state.update { it.copy(templates = templates).keptFilter() }
        }
    }

    fun onFilter(filter: TemplateFilter) = _state.update { it.copy(filter = filter) }

    /** Back to All when the chosen filter has nothing left. */
    private fun TemplatesState.keptFilter() = if (templates == null || filter in filters) this else copy(filter = TemplateFilter.All)
}
