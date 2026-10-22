package com.savie.precut.ai

import com.savie.precut.ai.cloud.CloudClient
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.ai.Transcript
import java.io.File
import javax.inject.Inject

/**
 * Speech to text with word timings, what automatic captions (Phase 21) are built on. The cloud
 * version is the first; an on-device one can sit behind the same interface later.
 */
interface Transcriber {
    /** [language]: a 2- or 3-letter code, or null to let it tell. Needs the user's [consent] for this audio. */
    suspend fun transcribe(audio: File, mimeType: String, durationMs: Long, language: String?, consent: UploadConsent): AiResult<Transcript>
}

/** Through PreCut's backend and its provider (docs/CLOUD_API.md). */
class CloudTranscriber @Inject constructor(private val client: CloudClient) : Transcriber {
    override suspend fun transcribe(audio: File, mimeType: String, durationMs: Long, language: String?, consent: UploadConsent) =
        client.transcribe(audio, mimeType, durationMs, language, consent)
}
