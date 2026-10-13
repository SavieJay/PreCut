package com.savie.precut.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.savie.precut.model.MediaInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class ImportedMedia(val uri: String, val info: MediaInfo)

data class ImportResult(val imported: List<ImportedMedia>, val failedCount: Int)

data class ImportedAudio(val uri: String, val durationMs: Long, val displayName: String)

interface MediaImporter {
    /** Keeps read access to [uris] across restarts and probes them, preserving order. */
    suspend fun import(uris: List<String>): ImportResult

    /** Keeps read access to an audio file and reads its duration and name; null if unreadable. */
    suspend fun importAudio(uri: String): ImportedAudio?
}

class AndroidMediaImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val probe: MediaProbe,
) : MediaImporter {

    override suspend fun import(uris: List<String>): ImportResult {
        val imported = mutableListOf<ImportedMedia>()
        var failed = 0
        for (uri in uris) {
            takePersistableReadPermission(uri)
            when (val result = probe.probe(uri)) {
                is ProbeResult.Success -> imported += ImportedMedia(uri, result.info)
                ProbeResult.Unsupported, is ProbeResult.Failed -> failed++
            }
        }
        return ImportResult(imported, failed)
    }

    override suspend fun importAudio(uri: String): ImportedAudio? {
        takePersistableReadPermission(uri)
        val duration = probe.probeAudioDurationMs(uri) ?: return null
        return ImportedAudio(uri, duration, displayName(uri))
    }

    private fun displayName(uri: String): String = try {
        context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.substringBeforeLast('.')
            .orEmpty()
    } catch (_: RuntimeException) {
        ""
    }

    private fun takePersistableReadPermission(uri: String) {
        try {
            context.contentResolver.takePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some picker fallbacks hand out non-persistable grants; access lasts for this session.
        }
    }
}
