package com.savie.precut.ui.editor

import com.savie.precut.media.MediaImporter
import com.savie.precut.media.broll.GalleryIndexer
import com.savie.precut.model.ClipType
import com.savie.precut.model.OverlayClip
import com.savie.precut.model.broll.GalleryItem
import com.savie.precut.model.broll.searchGallery
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.pro.ProFeature
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The B-roll sheet. */
sealed interface BrollUi {
    /** The app may not read the gallery yet: the sheet explains and asks. */
    data object NeedsAccess : BrollUi
    data class Indexing(val done: Int, val total: Int) : BrollUi
    data class Ready(val items: List<GalleryItem>, val query: String = "", val results: List<GalleryItem> = emptyList()) : BrollUi
}

/** How long a B-roll video plays at most, from its start (photos show for the default length). */
const val BROLL_MAX_MS = 4_000L

/**
 * Smart B-roll from your gallery (Phase 26B, Pro): asks for gallery access only when opened, looks
 * at the newest photos and videos on the phone, finds them by everyday words, and drops the picked
 * one over the video at the playhead, filling the frame, as an overlay (one undo step).
 */
class EditorBroll(
    private val host: EditorHost,
    private val pro: EditorPro,
    private val indexer: GalleryIndexer,
    private val importer: MediaImporter,
    private val photoDurationMs: () -> Long,
) {
    private val _state = MutableStateFlow<BrollUi?>(null)
    val state: StateFlow<BrollUi?> = _state.asStateFlow()
    private var job: Job? = null

    /** [hasAccess]: the gallery permission (or Android 14's partial access) is already granted. */
    fun open(hasAccess: Boolean) = pro.gate(ProFeature.SMART_BROLL) {
        if (hasAccess) index() else _state.value = BrollUi.NeedsAccess
    }

    fun onAccessResult(granted: Boolean) {
        if (granted) {
            index()
        } else {
            _state.value = null
            host.send(EditorEvent.BrollNoAccess)
        }
    }

    fun onQuery(query: String) = _state.update { ui ->
        (ui as? BrollUi.Ready)?.copy(query = query, results = searchGallery(ui.items, query, indexer.labelNames)) ?: ui
    }

    fun onPick(item: GalleryItem) {
        val at = host.positionMs
        _state.value = null
        host.scope.launch {
            val imported = importer.import(listOf(item.uri)).imported.firstOrNull()
            if (imported == null) {
                host.send(EditorEvent.NothingImported)
                return@launch
            }
            pro.spend(ProFeature.SMART_BROLL)
            val base = clipFromMedia(host.newId(), imported.uri, imported.info, photoDurationMs())
            val clip = base.copy(
                fit = FrameFit.FILL,
                transform = ClipTransform(),
                trimEndMs = if (base.type == ClipType.VIDEO) minOf(base.trimEndMs, BROLL_MAX_MS) else base.trimEndMs,
            )
            host.applyEdit(EditAction.AddOverlay(OverlayClip(clip, at)))
            host.send(EditorEvent.BrollAdded)
        }
    }

    fun onDismiss() {
        job?.cancel()
        _state.value = null
    }

    private fun index() {
        _state.value = BrollUi.Indexing(0, 0)
        job?.cancel()
        job = host.scope.launch {
            val items = indexer.index { done, total -> _state.update { if (it is BrollUi.Indexing) BrollUi.Indexing(done, total) else it } }
            _state.update { if (it is BrollUi.Indexing) BrollUi.Ready(items) else it }
        }
    }
}
