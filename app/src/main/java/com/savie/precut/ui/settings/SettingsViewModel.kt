package com.savie.precut.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.data.AppSettings
import com.savie.precut.data.AppStorage
import com.savie.precut.data.SettingsValues
import com.savie.precut.data.WebConfig
import com.savie.precut.di.IoDispatcher
import com.savie.precut.diagnostics.CrashLog
import com.savie.precut.diagnostics.appVersion
import com.savie.precut.media.export.ExportCapabilitiesProbe
import com.savie.precut.media.look.LutCache
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.export.ExportCapabilities
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.export.fittedTo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** What the Settings screen (H-09) shows besides the settings themselves. */
data class StorageInfo(val cacheBytes: Long? = null, val clearing: Boolean = false, val lutCount: Int = 0)

/** PreCut's website pages that Settings → About opens (Play asks for the privacy policy in the app). */
data class WebsitePages(val host: String) {
    val privacy: String get() = "https://$host/privacy"
    val terms: String get() = "https://$host/terms"
    val support: String get() = "https://$host/support"
}

/** Saved crash reports: how many, and when the newest was written (null for none). */
data class CrashInfo(val count: Int = 0, val newestMs: Long? = null)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val storage: AppStorage,
    private val luts: LutCache,
    private val probe: ExportCapabilitiesProbe,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    web: WebConfig,
) : ViewModel() {

    /** The website's pages About links to: privacy policy, terms and support. */
    val websitePages: WebsitePages = WebsitePages(web.host)

    val values: StateFlow<SettingsValues> = settings.values

    private val _storage = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storage.asStateFlow()

    private val _capabilities = MutableStateFlow(ExportCapabilities.BASIC)
    val capabilities: StateFlow<ExportCapabilities> = _capabilities.asStateFlow()

    private val _crashes = MutableStateFlow(CrashInfo())
    val crashes: StateFlow<CrashInfo> = _crashes.asStateFlow()

    /** "1.0.0 (19)". */
    val version: String = appVersion(context)

    init {
        viewModelScope.launch {
            _storage.value = StorageInfo(storage.cacheBytes(), lutCount = luts.customLuts().size)
            _capabilities.value = probe.capabilities
        }
        refreshCrashes()
    }

    fun onPhotoDuration(ms: Long) = settings.update { it.copy(photoDurationMs = ms) }

    fun onAspectRatio(ratio: AspectRatio) = settings.update { it.copy(aspectRatio = ratio) }

    /** Simple mode (Phase 24): the editor's toolbar shows a few tools, the rest under More. */
    fun onSimpleMode(simple: Boolean) = settings.update { it.copy(simpleMode = simple) }

    fun onExportSettings(export: ExportSettings) = settings.update { it.copy(export = export.fittedTo(_capabilities.value)) }

    /** The newest crash report, to share; null when there's none. */
    suspend fun newestCrashReport(): File? = withContext(ioDispatcher) { CrashLog.reports(context).firstOrNull() }

    fun onDeleteCrashReports() {
        viewModelScope.launch {
            withContext(ioDispatcher) { CrashLog.deleteAll(context) }
            refreshCrashes()
        }
    }

    private fun refreshCrashes() {
        viewModelScope.launch {
            _crashes.value = withContext(ioDispatcher) {
                val reports = CrashLog.reports(context)
                CrashInfo(reports.size, reports.firstOrNull()?.lastModified())
            }
        }
    }

    fun onClearCache() {
        if (_storage.value.clearing) return
        _storage.value = _storage.value.copy(clearing = true)
        viewModelScope.launch {
            storage.clearCache()
            _storage.value = _storage.value.copy(cacheBytes = storage.cacheBytes(), clearing = false)
        }
    }
}
