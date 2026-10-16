package com.savie.precut.ui.editor

import android.graphics.Bitmap
import com.savie.precut.data.AppSettings
import com.savie.precut.di.IoDispatcher
import com.savie.precut.export.ExportKind
import com.savie.precut.media.MediaSaver
import com.savie.precut.media.SavedMedia
import com.savie.precut.media.export.ExportCapabilitiesProbe
import com.savie.precut.media.export.FrameGrabber
import com.savie.precut.model.export.ExportCapabilities
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.export.fittedTo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/** The export settings sheet (EX-01): settings, Export video, Audio only and Save frame. */
class EditorExport(
    private val host: EditorHost,
    private val settingsStore: AppSettings,
    private val probe: ExportCapabilitiesProbe,
    private val frames: FrameGrabber,
    private val saver: MediaSaver,
    private val cacheDir: File,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    private val _capabilities = MutableStateFlow(ExportCapabilities.BASIC)
    val capabilities: StateFlow<ExportCapabilities> = _capabilities.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.values.value.export)
    val settings: StateFlow<ExportSettings> = _settings.asStateFlow()

    private val _savingFrame = MutableStateFlow(false)
    val savingFrame: StateFlow<Boolean> = _savingFrame.asStateFlow()

    fun onOpen() {
        if (host.ready?.project?.clips.isNullOrEmpty()) return
        _open.value = true
        host.scope.launch {
            val caps = withContext(io) { probe.capabilities }
            _capabilities.value = caps
            _settings.value = settingsStore.values.value.export.fittedTo(caps)
        }
    }

    fun onClose() {
        _open.value = false
    }

    fun onSettings(settings: ExportSettings) {
        _settings.value = settings.fittedTo(_capabilities.value)
    }

    /** Video or audio only: remembers the settings (the Settings default) and opens the export screen. */
    fun onExport(kind: ExportKind) {
        val settings = _settings.value
        settingsStore.update { it.copy(export = settings) }
        _open.value = false
        host.send(EditorEvent.NavigateExport(Json.encodeToString(ExportSettings.serializer(), settings), kind.name))
    }

    /** The frame at the playhead, at the chosen resolution, into Pictures/PreCut. */
    fun onSaveFrame() {
        val project = host.ready?.project ?: return
        if (_savingFrame.value) return
        _savingFrame.value = true
        val position = host.positionMs
        host.scope.launch {
            try {
                val saved = frames.grab(project, position, _settings.value.resolution.shortSidePx)?.let { save(it) } == true
                host.send(if (saved) EditorEvent.FrameSaved else EditorEvent.FrameNotSaved)
                if (saved) _open.value = false
            } finally {
                _savingFrame.value = false
            }
        }
    }

    private suspend fun save(bitmap: Bitmap): Boolean {
        val file = withContext(io) {
            try {
                File(File(cacheDir, FRAME_DIR).apply { mkdirs() }, "${UUID.randomUUID()}.jpg").also { f ->
                    f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                }
            } catch (_: IOException) {
                null
            }
        } ?: return false
        return saver.save(file, "PreCut_frame_${System.currentTimeMillis()}.jpg", SavedMedia.IMAGE).isSuccess
    }

    private companion object {
        const val FRAME_DIR = "frames"
        const val JPEG_QUALITY = 95
    }
}
