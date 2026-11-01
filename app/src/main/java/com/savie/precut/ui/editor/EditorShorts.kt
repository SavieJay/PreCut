package com.savie.precut.ui.editor

import android.graphics.Bitmap
import com.savie.precut.data.ProjectRepository
import com.savie.precut.export.ExportController
import com.savie.precut.media.PreviewController
import com.savie.precut.media.cutout.SubjectTracker
import com.savie.precut.media.cutout.TrackResult
import com.savie.precut.media.export.FrameGrabber
import com.savie.precut.media.shorts.MomentAnalyzer
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.edit.formatVariant
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.frame.SubjectSample
import com.savie.precut.model.pro.ProFeature
import com.savie.precut.model.shorts.MIN_LONG_VIDEO_MS
import com.savie.precut.model.shorts.findMoments
import com.savie.precut.model.shorts.shortOf
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Clock
import kotlin.math.roundToInt

/** A short made from the long video, saved as its own draft. */
data class ShortDraft(val projectId: String, val title: String, val durationMs: Long, val hookScore: Int, val thumbnail: Bitmap?)

sealed interface ShortsUi {
    data class Working(val percent: Int, val making: Boolean) : ShortsUi
    data class Results(val shorts: List<ShortDraft>) : ShortsUi
}

/**
 * Long video to shorts (Phase 26B, Pro): finds the best moments of this project on the phone
 * (loudness, picture changes, and the captions' words when there are some), and makes each an
 * upright short of its own draft: cut to the moment (captions stay lined up), 9:16, reframed on the
 * person. The original isn't changed.
 */
class EditorShorts(
    private val host: EditorHost,
    private val pro: EditorPro,
    private val preview: PreviewController,
    private val analyzer: MomentAnalyzer,
    private val tracker: SubjectTracker,
    private val frames: FrameGrabber,
    private val repository: ProjectRepository,
    private val exports: ExportController,
    private val clock: Clock,
    private val exportSettings: () -> ExportSettings,
) {
    private val _state = MutableStateFlow<ShortsUi?>(null)
    val state: StateFlow<ShortsUi?> = _state.asStateFlow()
    private var job: Job? = null

    fun open() = pro.gate(ProFeature.LONG_TO_SHORTS) {
        val project = host.ready?.project ?: return@gate
        if (project.totalDurationMs() < MIN_LONG_VIDEO_MS) {
            host.send(EditorEvent.ShortsTooShort)
            return@gate
        }
        if (job?.isActive == true) return@gate
        preview.pause()
        _state.value = ShortsUi.Working(0, making = false)
        job = host.scope.launch {
            try {
                val signals = analyzer.signals(project) { f -> progress(f * 0.5f, making = false) }
                val moments = findMoments(signals)
                if (moments.isEmpty()) {
                    _state.value = null
                    host.send(EditorEvent.ShortsNoneFound)
                    return@launch
                }
                pro.spend(ProFeature.LONG_TO_SHORTS)
                val now = clock.millis()
                val drafts = moments.mapIndexed { index, moment ->
                    val cut = project.shortOf(moment)
                    val samples = mutableMapOf<String, List<SubjectSample>>()
                    cut.clips.forEach { clip ->
                        val result = tracker.track(clip) { f -> progress(0.5f + (index + f / cut.clips.size) / moments.size * 0.5f, making = true) }
                        if (result is TrackResult.Done) samples[clip.id] = result.samples
                    }
                    val short = cut.formatVariant(AspectRatio.RATIO_9_16, samples).copy(
                        id = host.newId(),
                        name = listOf("Short ${index + 1}", moment.title).filter { it.isNotBlank() }.joinToString(" · "),
                        cover = null,
                        template = null,
                        createdAt = now + index,
                        updatedAt = now + index,
                    )
                    repository.save(short)
                    ShortDraft(short.id, moment.title, short.totalDurationMs(), moment.hookScore, frames.grab(short, 500L, THUMB_PX))
                }
                _state.value = ShortsUi.Results(drafts)
            } catch (e: CancellationException) {
                _state.value = null
                throw e
            }
        }
    }

    fun onOpen(projectId: String) {
        _state.value = null
        host.send(EditorEvent.OpenProject(projectId))
    }

    fun onExport(projectId: String) {
        _state.value = null
        host.send(EditorEvent.ExportProject(projectId, Json.encodeToString(ExportSettings.serializer(), exportSettings())))
    }

    /** Exports every short, one after another. */
    fun onExportAll() {
        val results = _state.value as? ShortsUi.Results ?: return
        val project = host.ready?.project ?: return
        host.scope.launch {
            val shorts = results.shorts.mapNotNull { repository.load(it.projectId) }
            val settings = exportSettings()
            if (exports.startVariants(project.id, shorts, settings)) {
                _state.value = null
                host.send(EditorEvent.NavigateExport(Json.encodeToString(ExportSettings.serializer(), settings), "VIDEO"))
            }
        }
    }

    fun onDismiss() {
        job?.cancel()
        _state.value = null
    }

    private fun progress(fraction: Float, making: Boolean) {
        val percent = (fraction * 100).roundToInt().coerceIn(0, 99)
        _state.update { if (it is ShortsUi.Working) ShortsUi.Working(percent, making) else it }
    }

    private companion object {
        const val THUMB_PX = 240
    }
}
