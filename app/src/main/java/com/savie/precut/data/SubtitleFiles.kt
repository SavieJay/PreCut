package com.savie.precut.data

import android.content.Context
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.savie.precut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Subtitle (.srt) files made from captions (Phase 21B): saved where the user picks, or shared. */
interface SubtitleFiles {
    /** Writes [text] into the document at [uri] the user picked; false when it can't be written. */
    suspend fun save(uri: String, text: String): Boolean

    /** [text] as [name] in app storage, for sharing: its content Uri, or null when it can't be written. */
    suspend fun forSharing(name: String, text: String): String?
}

class AndroidSubtitleFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SubtitleFiles {

    override suspend fun save(uri: String, text: String): Boolean = withContext(io) {
        try {
            val stream = context.contentResolver.openOutputStream(uri.toUri(), "wt") ?: return@withContext false
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            // The document was revoked or isn't writable.
            false
        }
    }

    override suspend fun forSharing(name: String, text: String): String? = withContext(io) {
        try {
            // Only the latest file is kept; one shared earlier has been handed over already.
            val dir = File(context.filesDir, DIR).apply { deleteRecursively(); mkdirs() }
            val file = File(dir, name).apply { writeText(text, Charsets.UTF_8) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        /** Shared through the app's FileProvider (res/xml/file_paths.xml). */
        const val DIR = "subtitles"
    }
}
