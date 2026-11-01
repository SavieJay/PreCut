package com.savie.precut.ai.cloud

import java.security.MessageDigest
import java.util.Base64

/**
 * The Play Integrity request hashes (docs/CLOUD_API.md): each verdict is bound to one request, and
 * the server recomputes the same value (supabase/functions/_shared/request_hash.ts). SHA-256,
 * URL-safe base64 without padding.
 */
internal object RequestHashes {

    fun register(installId: String): String = base64Url(sha256("precut-register:$installId".toByteArray()))

    fun transcribe(installId: String, audio: ByteArray): String =
        base64Url(sha256("precut-transcribe:$installId:${hex(sha256(audio))}".toByteArray()))

    /** An AI edit step: the install and the exact request body (the conversation so far). */
    fun edit(installId: String, body: ByteArray): String =
        base64Url(sha256("precut-edit:$installId:${hex(sha256(body))}".toByteArray()))

    /** A purchase sent for checking (Phase 25): the install and the purchase token. */
    fun purchase(installId: String, purchaseToken: String): String =
        base64Url(sha256("precut-purchase:$installId:${hex(sha256(purchaseToken.toByteArray()))}".toByteArray()))

    /** A caption translation (Phase 26B): the install and the exact request body. */
    fun translate(installId: String, body: ByteArray): String =
        base64Url(sha256("precut-translate:$installId:${hex(sha256(body))}".toByteArray()))

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
