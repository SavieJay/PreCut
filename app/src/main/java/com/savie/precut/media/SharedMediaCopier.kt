package com.savie.precut.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.savie.precut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/** Keeps media shared from other apps, whose read grants are temporary. */
interface SharedMediaStore {
    /** Copies each Uri into app storage, in order; unreadable ones are left out. */
    suspend fun copyIn(uris: List<String>): List<String>
}

class SharedMediaCopier @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SharedMediaStore {

    override suspend fun copyIn(uris: List<String>): List<String> = withContext(ioDispatcher) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        uris.mapNotNull { copy(Uri.parse(it), dir) }
    }

    private fun copy(source: Uri, dir: File): String? {
        val resolver = context.contentResolver
        val extension = resolver.getType(source)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
        val target = File(dir, "${UUID.randomUUID()}.$extension")
        return try {
            val input = resolver.openInputStream(source) ?: return null
            input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
            Uri.fromFile(target).toString()
        } catch (_: IOException) {
            target.delete()
            null
        } catch (_: SecurityException) {
            target.delete()
            null
        }
    }

    private companion object {
        const val DIR = "imports"
    }
}
