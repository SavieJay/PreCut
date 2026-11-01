package com.savie.precut.update

import android.content.Context
import androidx.core.content.edit
import com.savie.precut.model.update.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** The last update rules from the server (kept for offline starts) and what "What's new" needs. */
@Singleton
class UpdateStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var config: AppConfig?
        get() = prefs.getString(KEY_CONFIG, null)?.let {
            try {
                json.decodeFromString(AppConfig.serializer(), it)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        set(value) = prefs.edit { if (value == null) remove(KEY_CONFIG) else putString(KEY_CONFIG, json.encodeToString(AppConfig.serializer(), value)) }

    /** The version that ran last; null before the first start with Phase 23. */
    var lastRunVersionCode: Int?
        get() = prefs.getInt(KEY_LAST_RUN, -1).takeIf { it >= 0 }
        set(value) = prefs.edit { if (value == null) remove(KEY_LAST_RUN) else putInt(KEY_LAST_RUN, value) }

    /** Set after an update until "What's new" was seen: the version updated from. */
    var whatsNewFrom: Int?
        get() = prefs.getInt(KEY_WHATS_NEW_FROM, -1).takeIf { it >= 0 }
        set(value) = prefs.edit { if (value == null) remove(KEY_WHATS_NEW_FROM) else putInt(KEY_WHATS_NEW_FROM, value) }

    private companion object {
        const val PREFS = "updates"
        const val KEY_CONFIG = "config"
        const val KEY_LAST_RUN = "lastRunVersionCode"
        const val KEY_WHATS_NEW_FROM = "whatsNewFrom"
    }
}
