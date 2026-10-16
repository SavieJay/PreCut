package com.savie.precut.data

import android.content.Context
import androidx.core.content.edit
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.DEFAULT_IMAGE_DURATION_MS
import com.savie.precut.model.export.ExportSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide choices (H-09): new-project defaults and the export settings last used. */
@Serializable
data class SettingsValues(
    val photoDurationMs: Long = DEFAULT_IMAGE_DURATION_MS,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val export: ExportSettings = ExportSettings(),
    /** The notification prompt (A-03) was answered once; don't show it again. */
    val notificationsAsked: Boolean = false,
)

interface AppSettings {
    val values: StateFlow<SettingsValues>
    fun update(transform: (SettingsValues) -> SettingsValues)
}

@Singleton
class PrefsAppSettings @Inject constructor(@ApplicationContext context: Context) : AppSettings {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _values = MutableStateFlow(read())
    override val values: StateFlow<SettingsValues> = _values.asStateFlow()

    override fun update(transform: (SettingsValues) -> SettingsValues) {
        val next = transform(_values.value)
        if (next == _values.value) return
        _values.value = next
        prefs.edit { putString(KEY, json.encodeToString(SettingsValues.serializer(), next)) }
    }

    private fun read(): SettingsValues =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(SettingsValues.serializer(), it) }.getOrNull() } ?: SettingsValues()

    private companion object {
        const val PREFS = "settings"
        const val KEY = "values"
    }
}
