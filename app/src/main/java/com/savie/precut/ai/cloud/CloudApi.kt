package com.savie.precut.ai.cloud

import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.CloudStatus
import com.savie.precut.model.ai.Transcript
import com.savie.precut.model.ai.TranscriptWord
import com.savie.precut.model.ai.edit.EditStepReply
import com.savie.precut.model.pro.PlanState
import com.savie.precut.model.pro.ProPlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
    val editEnabled: Boolean = false,
    val editRunsLeft: Int = 0,
    val editRunsPerMonth: Int = 0,
    val pro: PlanDto? = null,
) {
    fun toModel() = CloudStatus(
        cloudEnabled, secondsLeft, secondsPerMonth, epochMs(resetsAt), maxUploadSeconds,
        editEnabled, editRunsLeft, editRunsPerMonth, pro?.toModel(),
    )
}

/** The server's plan for an install (docs/CLOUD_API.md, Phase 25). */
@Serializable
internal data class PlanDto(
    val pro: Boolean,
    val state: String? = null,
    val basePlanId: String? = null,
    val expiresAt: String? = null,
    val autoRenewing: Boolean = false,
    val trial: Boolean = false,
) {
    fun toModel() = ProPlan(
        pro = pro,
        state = state?.let { name -> PlanState.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } },
        basePlanId = basePlanId,
        expiresAtMs = expiresAt?.let(::epochMs),
        autoRenewing = autoRenewing,
        trial = trial,
    )
}

@Serializable
internal data class PurchaseRequest(val purchaseToken: String)

@Serializable
internal data class ShareTemplateRequest(val template: JsonElement)

@Serializable
internal data class ShareTemplateResponse(val id: String, val installToken: String? = null)

@Serializable
internal data class OpenTemplateResponse(val template: JsonElement)

/** A caption line to translate, or translated (Phase 26B). */
@Serializable
data class CaptionLineDto(val id: String, val text: String)

@Serializable
internal data class TranslateRequest(val lines: List<CaptionLineDto>, val target: String)

@Serializable
internal data class TranslateResponse(val lines: List<CaptionLineDto>, val editRunsLeft: Int = 0, val installToken: String? = null)

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

/** One step of an AI edit: the run it belongs to and the whole conversation so far. */
@Serializable
internal data class EditRequest(val runId: String, val messages: JsonArray)

@Serializable
internal data class EditResponse(
    val content: JsonArray,
    val stopReason: String,
    val stepsLeft: Int,
    val editRunsLeft: Int,
    val installToken: String? = null,
) {
    fun toModel() = EditStepReply(content, stopReason, stepsLeft, editRunsLeft)
}

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
    "runs_used" -> AiFailure.EditRunsUsed(error.resetsAt?.let(::epochMs))
    "steps_used" -> AiFailure.StepsUsed
    "bad_purchase" -> AiFailure.BadPurchase
    "templates_used" -> AiFailure.TooMany
    "bad_audio", "unsupported_audio" -> AiFailure.BadAudio
    else -> AiFailure.Busy
}

/** An ISO 8601 date-time ("2026-11-01T00:00:00+00:00") in epoch ms; null when it can't be read. */
internal fun epochMs(iso: String): Long? = try {
    OffsetDateTime.parse(iso).toInstant().toEpochMilli()
} catch (_: DateTimeParseException) {
    null
}
