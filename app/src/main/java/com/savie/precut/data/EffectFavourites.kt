package com.savie.precut.data

import android.content.Context
import androidx.core.content.edit
import com.savie.precut.model.effect.EffectType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Effects starred in the Effects panel, kept across projects. */
@Singleton
class EffectFavourites @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _favourites = MutableStateFlow(read())
    val favourites: StateFlow<Set<EffectType>> = _favourites.asStateFlow()

    fun toggle(type: EffectType) {
        val next = _favourites.value.let { if (type in it) it - type else it + type }
        _favourites.value = next
        prefs.edit { putStringSet(KEY, next.map { it.name }.toSet()) }
    }

    private fun read(): Set<EffectType> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { name -> EffectType.entries.firstOrNull { it.name == name } }.toSet()

    private companion object {
        const val PREFS = "effects"
        const val KEY = "favourites"
    }
}
