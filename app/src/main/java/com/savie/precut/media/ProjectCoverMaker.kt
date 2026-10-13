package com.savie.precut.media

import android.content.Context
import android.graphics.Bitmap
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.Project
import com.savie.precut.model.coverSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Draft covers: a JPEG of the chosen cover (default: the first clip's first frame), stored in app files. */
class ProjectCoverMaker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val thumbnails: ThumbnailLoader,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** What the cover depends on; null when there's nothing to show. */
    fun keyFor(project: Project): String? =
        project.coverSource()?.let { "${it.uri}|${it.type}|${it.sourceMs}|${it.rotationDegrees}" }

    /** Writes a new cover file (a fresh name per key, so image caches never show a stale one). */
    suspend fun make(project: Project, key: String): String? {
        val source = project.coverSource() ?: return null
        val bitmap = thumbnails.load(ThumbnailKey(source.uri, source.type, source.sourceMs, COVER_HEIGHT_PX, source.rotationDegrees, exact = true))
            ?: return null
        return withContext(ioDispatcher) {
            try {
                val dir = File(context.filesDir, COVER_DIR).apply { mkdirs() }
                val file = File(dir, "${project.id}-${key.hashCode().toUInt()}.jpg")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                file.absolutePath
            } catch (_: IOException) {
                null
            }
        }
    }

    fun delete(path: String?) {
        path?.let { File(it).delete() }
    }

    private companion object {
        const val COVER_DIR = "covers"
        const val COVER_HEIGHT_PX = 360
    }
}
