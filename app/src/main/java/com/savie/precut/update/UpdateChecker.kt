package com.savie.precut.update

import com.savie.precut.BuildConfig
import com.savie.precut.data.OpenProjects
import com.savie.precut.di.ApplicationScope
import com.savie.precut.export.ExportController
import com.savie.precut.export.ExportJobState
import com.savie.precut.model.update.AppConfig
import com.savie.precut.model.update.UpdatePrompt
import com.savie.precut.model.update.shouldCheck
import com.savie.precut.model.update.updatePrompt
import com.savie.precut.model.update.whatsNewAfterUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** What the app shows about updates: the prompt, and "What's new" bullets to show once. */
data class UpdateUi(val prompt: UpdatePrompt = UpdatePrompt.None, val whatsNew: List<String> = emptyList())

/**
 * Forced updates (Phase 23): asks the server for the update rules on a cold start and whenever the
 * app comes back to the front (at most every 15 minutes; no background polling), keeps the last
 * answer for offline starts, and turns it into [state] with the pure rules in `model/update`. A
 * required update waits for a running export, and open projects are saved before it shows.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val store: UpdateStore,
    private val source: AppConfigSource,
    exports: ExportController,
    private val openProjects: OpenProjects,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val versionCode = BuildConfig.VERSION_CODE
    private val config = MutableStateFlow(store.config)
    private val whatsNewFrom = MutableStateFlow(store.whatsNewFrom)
    private var lastCheckMs: Long? = null

    init {
        // A newer version than last time: its "What's new" waits until seen.
        val lastRun = store.lastRunVersionCode
        if (lastRun != null && lastRun < versionCode) {
            store.whatsNewFrom = lastRun
            whatsNewFrom.value = lastRun
        }
        store.lastRunVersionCode = versionCode
    }

    val state: StateFlow<UpdateUi> = combine(config, exports.state, whatsNewFrom) { config, export, from ->
        val exporting = export is ExportJobState.Running || export is ExportJobState.Saving
        UpdateUi(updatePrompt(versionCode, config, exporting), whatsNewAfterUpdate(versionCode, from, config))
    }.stateIn(scope, SharingStarted.Eagerly, UpdateUi(updatePrompt(versionCode, store.config, exporting = false)))

    init {
        // Unsaved edits are written before the blocking screen covers the editor.
        scope.launch {
            var blocked = false
            state.collect { ui ->
                val required = ui.prompt is UpdatePrompt.Required
                if (required && !blocked) openProjects.flushAll()
                blocked = required
            }
        }
    }

    /** The app came to the front (a cold start included). */
    fun onForeground() {
        val now = clock.millis()
        if (!shouldCheck(now, lastCheckMs)) return
        lastCheckMs = now
        scope.launch {
            // Offline or failing: keep the last answer (none never blocks).
            val fresh: AppConfig = source.fetch() ?: return@launch
            store.config = fresh
            config.value = fresh
        }
    }

    fun onWhatsNewSeen() {
        store.whatsNewFrom = null
        whatsNewFrom.value = null
    }
}
