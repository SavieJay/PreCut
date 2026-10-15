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
    private val copies: SharedMediaStore,
) : MediaImporter {

    override suspend fun import(uris: List<String>): ImportResult {
        val imported = mutableListOf<ImportedMedia>()
        var failed = 0
        for (uri in uris) {
            val kept = keepReadable(uri)
            val result = kept?.let { probe.probe(it) }
            if (kept != null && result is ProbeResult.Success) imported += ImportedMedia(kept, result.info) else failed++
        }
        return ImportResult(imported, failed)
    }

    override suspend fun importAudio(uri: String): ImportedAudio? {
        val kept = keepReadable(uri) ?: return null
        val duration = probe.probeAudioDurationMs(kept) ?: return null
        // The name comes from the original: a private copy is named by a random id.
        return ImportedAudio(kept, duration, displayName(uri))
    }

    /**
     * A Uri the app can still read after it's closed: the picked one when its read grant can be
     * made persistent, otherwise a private copy (some pickers only grant access until the app
     * process ends, after which the clip would show as missing). Null when it can't be read.
     */
    private suspend fun keepReadable(uri: String): String? {
        // App-owned files (camera captures, copies of shared media) need no grant.
        if (Uri.parse(uri).scheme != "content") return uri
        if (takePersistableReadPermission(uri)) return uri
        return copies.copyIn(listOf(uri)).firstOrNull()
    }

    private fun displayName(uri: String): String = try {
        context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.substringBeforeLast('.')
            .orEmpty()
    } catch (_: RuntimeException) {
        ""
    }

    /** Whether the app now holds a read grant for [uri] that survives restarts. */
    private fun takePersistableReadPermission(uri: String): Boolean {
        val parsed = Uri.parse(uri)
        val resolver = context.contentResolver
        try {
            resolver.takePersistableUriPermission(parsed, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some pickers hand out grants that can't be made persistent.
            return false
        }
        // Some providers accept the call without keeping anything, so check the grant is really held.
        return resolver.persistedUriPermissions.any { it.uri == parsed && it.isReadPermission }
    }
}
