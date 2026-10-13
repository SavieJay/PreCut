package com.savie.precut.data

import android.content.Context
import android.net.Uri
import com.savie.precut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Deletes recorded voiceovers no saved project uses any more. Run only while no editor is open:
 * an open editor's undo history may still need a file its current project no longer references.
 */
interface UnusedMediaCleaner {
    suspend fun sweep()
}

class VoiceoverJanitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UnusedMediaCleaner {
    override suspend fun sweep() {
        val referenced = repository.loadAll().flatMap { project -> project.audioClips.map { it.uri } }.toSet()
        withContext(ioDispatcher) {
            File(context.filesDir, "voiceovers").listFiles()
                ?.filter { Uri.fromFile(it).toString() !in referenced }
                ?.forEach { it.delete() }
        }
    }
}
