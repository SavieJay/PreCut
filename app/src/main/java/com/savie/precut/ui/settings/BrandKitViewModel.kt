package com.savie.precut.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.data.AppSettings
import com.savie.precut.media.MediaImporter
import com.savie.precut.model.brand.BrandKit
import com.savie.precut.model.brand.BrandMedia
import com.savie.precut.model.brand.MAX_BRAND_COLORS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which brand kit slot a picked file goes to. */
enum class BrandSlot { LOGO, INTRO, OUTRO }

/** Settings → Brand kit (Phase 26B): the logo, colours, fonts, intro and outro, saved once. */
@HiltViewModel
class BrandKitViewModel @Inject constructor(
    private val settings: AppSettings,
    private val importer: MediaImporter,
) : ViewModel() {
    val kit: StateFlow<BrandKit> = settings.values.map { it.brandKit }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.values.value.brandKit)

    private val _failed = MutableStateFlow(false)

    /** The last picked file couldn't be opened. */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private fun update(transform: (BrandKit) -> BrandKit) = settings.update { it.copy(brandKit = transform(it.brandKit)) }

    /** Keeps lasting access to the picked file and saves it in [slot]. */
    fun onPicked(slot: BrandSlot, uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val media = importer.import(listOf(uri)).imported.firstOrNull()
            _failed.value = media == null
            media ?: return@launch
            val saved = BrandMedia(media.uri, media.info.type, media.info.durationMs, media.info.width, media.info.height, media.info.rotationDegrees)
            update {
                when (slot) {
                    BrandSlot.LOGO -> it.copy(logo = saved)
                    BrandSlot.INTRO -> it.copy(intro = saved)
                    BrandSlot.OUTRO -> it.copy(outro = saved)
                }
            }
        }
    }

    fun onRemove(slot: BrandSlot) = update {
        when (slot) {
            BrandSlot.LOGO -> it.copy(logo = null)
            BrandSlot.INTRO -> it.copy(intro = null)
            BrandSlot.OUTRO -> it.copy(outro = null)
        }
    }

    /** Sets colour [index] (a new one past the end), up to five. */
    fun onColor(index: Int, argb: Int) = update { kit ->
        val colors = kit.colors.toMutableList()
        if (index < colors.size) colors[index] = argb else if (colors.size < MAX_BRAND_COLORS) colors += argb
        kit.copy(colors = colors)
    }

    fun onRemoveColor(index: Int) = update { it.copy(colors = it.colors.filterIndexed { i, _ -> i != index }) }

    fun onTitleFont(fontId: String?) = update { it.copy(titleFontId = fontId) }

    fun onBodyFont(fontId: String?) = update { it.copy(bodyFontId = fontId) }

    fun onApplyToNew(apply: Boolean) = update { it.copy(applyToNew = apply) }
}
