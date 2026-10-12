package com.savie.precut.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.savie.precut.model.MediaInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class ImportedMedia(val uri: String, val info: MediaInfo)

data class ImportResult(val imported: List<ImportedMedia>, val failedCount: Int)

interface MediaImporter {
    /** Keeps read access to [uris] across restarts and probes them, preserving order. */
    suspend fun import(uris: List<String>): ImportResult
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
