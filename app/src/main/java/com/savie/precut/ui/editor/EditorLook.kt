package com.savie.precut.ui.editor

import com.savie.precut.media.look.CustomLut
import com.savie.precut.media.look.LutCache
import com.savie.precut.model.look.Adjustment
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.look.CurveChannel
import com.savie.precut.model.look.CurvePoint
import com.savie.precut.model.look.HslBand
import com.savie.precut.model.look.HslColor
import com.savie.precut.model.look.LookLayer
import com.savie.precut.model.clipById
import com.savie.precut.model.edit.BatchEdits
import com.savie.precut.model.edit.EditAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Filters, Adjust, HSL and Curves for the selected look layer, else the selected clip (or the
 * one under the playhead). With nothing selected, Filters/Adjust add a layer at the playhead, as
 * in CapCut. Also owns look layers' timeline actions and imported `.cube` LUTs.
 */
class EditorLook(private val host: EditorHost, private val luts: LutCache) {

    private val _customLuts = MutableStateFlow<List<CustomLut>>(emptyList())
    val customLuts: StateFlow<List<CustomLut>> = _customLuts.asStateFlow()

    init {
        host.scope.launch { _customLuts.value = luts.customLuts() }
    }

    /** The look being edited, if any. */
    fun current(ready: EditorUiState.Ready): ClipLook? =
        ready.selectedLayer()?.look ?: ready.project.clipById(ready.selectedClipId)?.look

    /** Opens a look tool (FILTER, ADJUST, HSL, CURVES) on the layer or clip, or adds a layer. */
    fun open(tool: EditorTool) {
        val ready = host.ready ?: return
        when {
            ready.selectedLayerId != null -> host.update { it.copy(activeTool = tool) }
            ready.selectedClipId != null -> host.update { it.copy(activeTool = tool) }
            else -> addLayer(tool)
        }
    }

    private fun addLayer(tool: EditorTool) {
        val ready = host.ready ?: return
        if (ready.project.clips.isEmpty()) return
        val start = host.positionMs
        val layer = LookLayer(host.newId(), start, start + DEFAULT_LAYER_MS)
        host.applyEdit(EditAction.AddLookLayer(layer))
        host.update { it.copy(selectedLayerId = layer.id, selectedClipId = null, selectedTextId = null, selectedAudioId = null, activeTool = tool) }
    }

    fun onFilter(filterId: String?) = change { it.copy(filterId = filterId, filterIntensity = if (filterId == null) 1f else it.filterIntensity) }

    fun onIntensity(intensity: Float) = change { it.copy(filterIntensity = intensity) }

    fun onAdjust(adjustment: Adjustment, value: Float) = change { it.copy(adjust = it.adjust.with(adjustment, value)) }

    fun onResetAdjust() = change { it.copy(adjust = ClipLook().adjust, hsl = ClipLook.DEFAULT_HSL, curves = ClipLook().curves) }

    fun onHsl(color: HslColor, band: HslBand) =
        change { look -> look.copy(hsl = look.hsl.toMutableList().also { it[color.ordinal] = band }) }

    fun onCurve(channel: CurveChannel, points: List<CurvePoint>) = change { it.copy(curves = it.curves.with(channel, points)) }

    /** "Apply to all" from the Filter panel (filter only) or the Adjust panel (Adjust, HSL, Curves). */
    fun onApplyAll(filterOnly: Boolean) {
        val ready = host.ready ?: return
        val look = current(ready) ?: return
        val all = BatchEdits.allClipIds(ready.project)
        host.applyEdit(
            if (filterOnly) BatchEdits.filter(ready.project, all, look.filterId, look.filterIntensity) else BatchEdits.adjust(ready.project, all, look),
        )
    }

    fun onImportLut(uri: String?) {
        if (uri == null) return
        host.scope.launch {
            luts.import(uri)
                .onSuccess { imported ->
                    _customLuts.value = _customLuts.value + imported
                    onFilter(imported.id)
                }
                .onFailure { host.send(EditorEvent.LutImportFailed) }
        }
    }

    // Layers on the timeline

    fun onLayerSelected(layerId: String) = host.update {
        it.copy(selectedLayerId = layerId, selectedClipId = null, selectedTextId = null, selectedAudioId = null, activeTool = null, audioMenuOpen = false)
    }

    fun onLayerTiming(layerId: String, startMs: Long, endMs: Long) {
        val layer = host.ready?.project?.lookLayers?.firstOrNull { it.id == layerId } ?: return
        host.applyEdit(EditAction.UpdateLookLayer(layer.copy(startMs = startMs, endMs = endMs)))
    }

    fun onSplitLayer() {
        val id = host.ready?.selectedLayerId ?: return
        val newId = host.newId()
        host.applyEdit(EditAction.SplitLookLayer(id, host.positionMs, newId))
        if (host.ready?.project?.lookLayers?.any { it.id == newId } == true) host.update { it.copy(selectedLayerId = newId) }
    }

    fun onDuplicateLayer() {
        val id = host.ready?.selectedLayerId ?: return
        val newId = host.newId()
        host.applyEdit(EditAction.DuplicateLookLayer(id, newId))
        host.update { it.copy(selectedLayerId = newId) }
    }

    fun onDeleteLayer() {
        val id = host.ready?.selectedLayerId ?: return
        host.applyEdit(EditAction.DeleteLookLayer(id))
        host.update { it.copy(selectedLayerId = null, activeTool = null) }
    }

    /** Before/after peek: toggles showing the original in the preview (tap the selected filter again). */
    fun onPeek() {
        host.update { it.copy(peekOriginal = !it.peekOriginal) }
        host.refreshPreview()
    }

    private fun change(transform: (ClipLook) -> ClipLook) {
        if (host.ready?.peekOriginal == true) host.update { it.copy(peekOriginal = false) }
        val ready = host.ready ?: return
        val layer = ready.selectedLayer()
        when {
            layer != null -> host.applyEdit(EditAction.UpdateLookLayer(layer.copy(look = transform(layer.look))))
            ready.selectedClipId != null -> {
                val clip = ready.project.clipById(ready.selectedClipId) ?: return
                host.applyEdit(EditAction.SetClipLook(clip.id, transform(clip.look)))
            }
        }
    }

    private fun EditorUiState.Ready.selectedLayer(): LookLayer? = project.lookLayers.firstOrNull { it.id == selectedLayerId }

    private companion object {
        const val DEFAULT_LAYER_MS = 3_000L
    }
}
