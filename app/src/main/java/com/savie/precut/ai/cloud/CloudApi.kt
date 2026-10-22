package com.savie.precut.ai.cloud

import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.CloudStatus
import com.savie.precut.model.ai.Transcript
import com.savie.precut.model.ai.TranscriptWord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** The `cloud` function's JSON (docs/CLOUD_API.md), turned into the app's own types. */

internal const val API_VERSION = 1

internal val cloudJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class StatusDto(
    val cloudEnabled: Boolean,
    val secondsLeft: Int,
    val secondsPerMonth: Int,
    val resetsAt: String,
    val maxUploadSeconds: Int,
) {
    fun toModel() = CloudStatus(cloudEnabled, secondsLeft, secondsPerMonth, epochMs(resetsAt), maxUploadSeconds)
}

@Serializable
internal data class RegisterRequest(val installId: String)

@Serializable
internal data class RegisterResponse(val installToken: String, val status: StatusDto)

@Serializable
internal data class StatusResponse(val status: StatusDto, val installToken: String? = null)

@Serializable
internal data class WordDto(val text: String, val startMs: Long, val endMs: Long)

@Serializable
internal data class TranscriptDto(val language: String? = null, val durationMs: Long, val words: List<WordDto>) {
    fun toModel() = Transcript(language, durationMs, words.map { TranscriptWord(it.text, it.startMs, it.endMs) })
}

@Serializable
internal data class TranscribeResponse(val transcript: TranscriptDto, val secondsLeft: Int, val installToken: String? = null)

@Serializable
internal data class ErrorDto(
    val code: String,
    val secondsLeft: Int? = null,
    val resetsAt: String? = null,
    val maxUploadSeconds: Int? = null,
)

@Serializable
internal data class ErrorResponse(val error: ErrorDto)

/**
 * What a refusal means to the app. `not_registered` is handled before this (the client registers
 * again once); anything unexpected is [AiFailure.Busy], worth trying again later.
 */
internal fun failureFor(error: ErrorDto): AiFailure = when (error.code) {
    "upgrade_required" -> AiFailure.UpdateApp
    "app_not_verified" -> AiFailure.AppNotVerified
    "device_not_verified" -> AiFailure.DeviceNotVerified
    "quota_used" -> AiFailure.QuotaUsed(error.secondsLeft ?: 0, error.resetsAt?.let(::epochMs))
    "budget_reached" -> AiFailure.BudgetReached(error.resetsAt?.let(::epochMs))
    "paused" -> AiFailure.Paused
    "too_long" -> AiFailure.TooLong(error.maxUploadSeconds ?: 0)
    "too_large" -> AiFailure.TooLarge
    "bad_audio", "unsupported_audio" -> AiFailure.BadAudio
    else -> AiFailure.Busy
}

/** An ISO 8601 date-time ("2026-11-01T00:00:00+00:00") in epoch ms; null when it can't be read. */
internal fun epochMs(iso: String): Long? = try {
    OffsetDateTime.parse(iso).toInstant().toEpochMilli()
} catch (_: DateTimeParseException) {
    null
}
