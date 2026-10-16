package com.savie.precut.ui.settings

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.data.AppSettings
import com.savie.precut.data.AppStorage
import com.savie.precut.data.SettingsValues
import com.savie.precut.media.export.ExportCapabilitiesProbe
import com.savie.precut.media.look.LutCache
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.export.ExportCapabilities
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.export.fittedTo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Settings screen (H-09) shows besides the settings themselves. */
data class StorageInfo(val cacheBytes: Long? = null, val clearing: Boolean = false, val lutCount: Int = 0)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val storage: AppStorage,
    private val luts: LutCache,
    private val probe: ExportCapabilitiesProbe,
    @ApplicationContext context: Context,
) : ViewModel() {

    val values: StateFlow<SettingsValues> = settings.values

    private val _storage = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storage.asStateFlow()

    private val _capabilities = MutableStateFlow(ExportCapabilities.BASIC)
    val capabilities: StateFlow<ExportCapabilities> = _capabilities.asStateFlow()

    /** "1.0 (1)". */
    val version: String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        "${info.versionName} ($code)"
    }.getOrDefault("")

    init {
        viewModelScope.launch {
            _storage.value = StorageInfo(storage.cacheBytes(), lutCount = luts.customLuts().size)
            _capabilities.value = probe.capabilities
        }
    }

    fun onPhotoDuration(ms: Long) = settings.update { it.copy(photoDurationMs = ms) }

    fun onAspectRatio(ratio: AspectRatio) = settings.update { it.copy(aspectRatio = ratio) }

    fun onExportSettings(export: ExportSettings) = settings.update { it.copy(export = export.fittedTo(_capabilities.value)) }

    fun onClearCache() {
        if (_storage.value.clearing) return
        _storage.value = _storage.value.copy(clearing = true)
        viewModelScope.launch {
            storage.clearCache()
            _storage.value = _storage.value.copy(cacheBytes = storage.cacheBytes(), clearing = false)
        }
    }
}
