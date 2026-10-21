package com.savie.precut.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Templates starred, and the ones used lately (most recent first). */
interface TemplateHistory {
    val favourites: StateFlow<Set<String>>
    val recent: StateFlow<List<String>>
    fun toggleFavourite(templateId: String)
    fun used(templateId: String)
}

@Singleton
class PrefsTemplateHistory @Inject constructor(@ApplicationContext context: Context) : TemplateHistory {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _favourites = MutableStateFlow(prefs.getStringSet(FAVOURITES, emptySet()).orEmpty().toSet())
    override val favourites: StateFlow<Set<String>> = _favourites.asStateFlow()
    private val _recent = MutableStateFlow(prefs.getString(RECENT, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty())
    override val recent: StateFlow<List<String>> = _recent.asStateFlow()

    override fun toggleFavourite(templateId: String) {
        val next = _favourites.value.let { if (templateId in it) it - templateId else it + templateId }
        _favourites.value = next
        prefs.edit { putStringSet(FAVOURITES, next) }
    }

    override fun used(templateId: String) {
        val next = (listOf(templateId) + _recent.value.filterNot { it == templateId }).take(MAX_RECENT)
        _recent.value = next
        prefs.edit { putString(RECENT, next.joinToString(SEPARATOR)) }
    }

    private companion object {
        const val PREFS = "templates"
        const val FAVOURITES = "favourites"
        const val RECENT = "recent"
        const val SEPARATOR = "\n"
        const val MAX_RECENT = 12
    }
}
