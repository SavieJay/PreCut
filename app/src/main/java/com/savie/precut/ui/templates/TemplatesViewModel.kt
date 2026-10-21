package com.savie.precut.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.data.StoredTemplate
import com.savie.precut.data.TemplateStore
import com.savie.precut.model.template.TemplateCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Templates tab: [templates] null while loading; [shown] filtered by [category] (null: all). */
data class TemplatesState(val templates: List<StoredTemplate>? = null, val category: TemplateCategory? = null) {
    val shown: List<StoredTemplate>? get() = templates?.filter { category == null || it.template.category == category }
    /** Only the categories that have templates, so a chip never leads to an empty grid. */
    val categories: List<TemplateCategory> get() = templates.orEmpty().map { it.template.category }.distinct().sorted()
}

@HiltViewModel
class TemplatesViewModel @Inject constructor(private val store: TemplateStore) : ViewModel() {
    private val _state = MutableStateFlow(TemplatesState())
    val state: StateFlow<TemplatesState> = _state.asStateFlow()

    /** Reads the templates again (one may have been saved or deleted since). */
    fun refresh() {
        viewModelScope.launch {
            val templates = store.list()
            _state.update { it.copy(templates = templates, category = it.category?.takeIf { category -> templates.any { t -> t.template.category == category } }) }
        }
    }

    fun onCategory(category: TemplateCategory?) = _state.update { it.copy(category = category) }
}
