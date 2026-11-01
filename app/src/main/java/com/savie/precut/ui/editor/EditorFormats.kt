package com.savie.precut.ui.editor

import android.graphics.Bitmap
import com.savie.precut.export.ExportController
import com.savie.precut.media.PreviewController
import com.savie.precut.media.cutout.SubjectTracker
import com.savie.precut.media.cutout.TrackResult
import com.savie.precut.media.export.FrameGrabber
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.Project
import com.savie.precut.model.edit.EXPORT_FORMATS
import com.savie.precut.model.edit.formatVariant
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.frame.SubjectSample
import com.savie.precut.model.pro.ProFeature
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** One shape of the multi-format export: the reframed edit, a frame of it, and whether it's ticked. */
data class FormatChoice(val ratio: AspectRatio, val project: Project, val preview: Bitmap?, val selected: Boolean = true)

/** The multi-format sheet: finding the person ([percent]), then the shapes to export. */
data class FormatsUi(val percent: Int? = 0, val choices: List<FormatChoice> = emptyList())

/**
 * One-tap multi-format export (Phase 26B, Pro): the edit as 9:16, 1:1 and 16:9 at once, each
 * auto-reframed on the person, with a frame of each to check, then exported one after another.
 */
class EditorFormats(
    private val host: EditorHost,
    private val pro: EditorPro,
    private val preview: PreviewController,
    private val tracker: SubjectTracker,
    private val frames: FrameGrabber,
    private val exports: ExportController,
    private val exportSettings: () -> ExportSettings,
) {
    private val _state = MutableStateFlow<FormatsUi?>(null)
    val state: StateFlow<FormatsUi?> = _state.asStateFlow()
    private var job: Job? = null

    fun open() = pro.gate(ProFeature.MULTI_FORMAT_EXPORT) {
        val project = host.ready?.project ?: return@gate
        if (project.clips.isEmpty() || job?.isActive == true) return@gate
        preview.pause()
        _state.value = FormatsUi()
        job = host.scope.launch {
            try {
                val samples = mutableMapOf<String, List<SubjectSample>>()
                project.clips.forEachIndexed { index, clip ->
                    val result = tracker.track(clip) { fraction ->
                        val percent = ((index + fraction) / project.clips.size * 100).roundToInt().coerceIn(0, 99)
                        _state.update { it?.copy(percent = percent) }
                    }
                    if (result is TrackResult.Done) samples[clip.id] = result.samples
                }
                val at = (project.totalDurationMs() / 3).coerceAtMost(3_000L)
                val choices = EXPORT_FORMATS.map { ratio ->
                    val variant = project.formatVariant(ratio, samples)
                    FormatChoice(ratio, variant, frames.grab(variant, at, PREVIEW_PX))
                }
                _state.value = FormatsUi(percent = null, choices = choices)
            } catch (e: CancellationException) {
                _state.value = null
                throw e
            }
        }
    }

    fun onToggle(ratio: AspectRatio) = _state.update { ui ->
        ui?.copy(choices = ui.choices.map { if (it.ratio == ratio) it.copy(selected = !it.selected) else it })
    }

    /** Exports the ticked shapes, one after another, and opens the export screen to follow them. */
    fun onExport() {
        val ui = _state.value ?: return
        val ready = host.ready ?: return
        val chosen = ui.choices.filter { it.selected }.map { it.project }
        if (chosen.isEmpty()) return
        val settings = exportSettings()
        if (!exports.startVariants(ready.project.id, chosen, settings)) return
        pro.spend(ProFeature.MULTI_FORMAT_EXPORT)
        _state.value = null
        host.send(EditorEvent.NavigateExport(Json.encodeToString(ExportSettings.serializer(), settings), "VIDEO"))
    }

    fun onDismiss() {
        job?.cancel()
        _state.value = null
    }

    private companion object {
        const val PREVIEW_PX = 320
    }
}
