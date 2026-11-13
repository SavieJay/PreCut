package com.savie.precut.data

import android.content.Context
import android.net.Uri
import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.ProxyManager
import com.savie.precut.media.ProxyRules
import com.savie.precut.model.Project
import com.savie.precut.model.mediaUris
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Deletes app-owned media (and preview proxies) no saved project uses any more. Run only while no editor is open:
 * an open editor's undo history may still need a file its current project no longer references.
 */
interface UnusedMediaCleaner {
    suspend fun sweep()
}

/** App-private media folders: recorded voiceovers, camera captures, copies of picked or shared files, freeze frames, reversed clips, drawings, background-removal mattes, files downloaded from the account (Phase 30D). */
val APP_MEDIA_DIRS = listOf("voiceovers", "captures", "imports", "freezes", "reversed", "drawings", "mattes", "cloud")

class AppMediaJanitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val proxies: ProxyManager,
    private val settings: AppSettings,
) : UnusedMediaCleaner {
    override suspend fun sweep() {
        val projects = repository.loadAll()
        // The brand kit's files (Phase 26B) are kept even while no project uses them.
        val brand = settings.values.value.brandKit.let { listOfNotNull(it.logo?.uri, it.intro?.uri, it.outro?.uri) }
        val referenced = (projects.flatMap(::mediaUris) + brand).toSet()
        proxies.trim(projects.flatMap { it.clips }.filter(ProxyRules::needsProxy).map { it.uri }.toSet())
        withContext(ioDispatcher) {
            unusedFiles(APP_MEDIA_DIRS.map { File(context.filesDir, it) }, referenced) { Uri.fromFile(it).toString() }
                .forEach { it.delete() }
        }
    }

    companion object {
        /** Every media Uri a project points at ([Project.mediaUris]). */
        fun mediaUris(project: Project): List<String> = project.mediaUris()

        /** Files in [dirs] whose Uri (per [uriOf]) isn't in [referenced]. */
        fun unusedFiles(dirs: List<File>, referenced: Set<String>, uriOf: (File) -> String): List<File> =
            dirs.flatMap { it.listFiles()?.toList().orEmpty() }.filter { it.isFile && uriOf(it) !in referenced }
    }
}
