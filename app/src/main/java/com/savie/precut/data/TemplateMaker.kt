package com.savie.precut.data

import android.util.Log
import com.savie.precut.di.ApplicationScope
import com.savie.precut.media.ExportState
import com.savie.precut.media.VideoExporter
import com.savie.precut.media.export.FrameGrabber
import com.savie.precut.model.Project
import com.savie.precut.model.export.ExportBitrate
import com.savie.precut.model.export.ExportResolution
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.template.TemplateCategory
import com.savie.precut.model.template.toTemplate
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Save as template: the template is stored at once with a thumbnail (the middle frame); its preview
 * video (a small export of the source project) renders in the background, so leaving the editor
 * doesn't stop it. Until then, or if it fails, the card shows the thumbnail.
 */
@Singleton
class TemplateMaker @Inject constructor(
    private val store: TemplateStore,
    private val exporter: VideoExporter,
    private val frames: FrameGrabber,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend fun save(project: Project, title: String, category: TemplateCategory, slotIds: Set<String>, textIds: Set<String>): StoredTemplate {
        val template = project.toTemplate(UUID.randomUUID().toString(), title, category, slotIds, textIds, clock.millis())
        val stored = store.save(template)
        store.attach(template.id, thumbnail = frames.grab(project, project.totalDurationMs() / 2, THUMBNAIL_SHORT_SIDE_PX))
        scope.launch {
            when (val result = exporter.export(project, PREVIEW_SETTINGS).last()) {
                is ExportState.Success -> store.attach(template.id, preview = result.file)
                is ExportState.Failed -> Log.w(TAG, "Template preview didn't render", result.cause)
                else -> Unit
            }
        }
        return stored
    }

    private companion object {
        const val TAG = "TemplateMaker"
        const val THUMBNAIL_SHORT_SIDE_PX = 360
        val PREVIEW_SETTINGS = ExportSettings(resolution = ExportResolution.P480, frameRate = 30, bitrate = ExportBitrate.LOWER)
    }
}
