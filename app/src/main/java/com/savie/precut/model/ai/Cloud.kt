package com.savie.precut.model.ai

/**
 * What cloud AI (Phase 20) gives and how it can fail, independent of the backend's wire format
 * (docs/CLOUD_API.md) and of the provider behind it.
 */

/** One recognised word and where it is in the audio. */
data class TranscriptWord(val text: String, val startMs: Long, val endMs: Long)

/** Speech to text's result: the language heard (the provider's code, e.g. "eng"), the audio's length and its words. */
data class Transcript(val language: String?, val durationMs: Long, val words: List<TranscriptWord>)

/** This install's free minutes this month, and the limits. */
data class CloudStatus(
    /** False while cloud AI is paused or this month's budget for everyone is used up. */
    val cloudEnabled: Boolean,
    val secondsLeft: Int,
    val secondsPerMonth: Int,
    /** When the free minutes start again; null if the server's date couldn't be read. */
    val resetsAtMs: Long?,
    val maxUploadSeconds: Int,
)

/**
 * What a cloud feature is about to send, as the consent dialog shows it. Long sound goes in
 * several uploads: [partBytes] are their sizes in order (empty for one upload of [bytes]).
 */
data class UploadSummary(val feature: AiFeature, val durationMs: Long, val bytes: Long, val partBytes: List<Long> = emptyList()) {
    /** The uploads this consent covers, in order. */
    val uploads: List<Long> get() = partBytes.ifEmpty { listOf(bytes) }
}

enum class AiFeature {
    /** Settings → Check cloud AI: a test phrase spoken by the phone. */
    CHECK,
    /** Automatic captions (Phase 21). */
    CAPTIONS,
}

/** Why a cloud AI request didn't succeed; the UI shows a message for each. */
sealed interface AiFailure {
    /** Switched off in Settings. */
    data object CloudOff : AiFailure

    /** This build has no backend configured (no cloud.properties). */
    data object NotSetUp : AiFailure

    data object Offline : AiFailure

    /** The Play Store or Google Play services is missing or out of date, so the app can't be verified. */
    data object NoPlayServices : AiFailure

    /** Google Play couldn't confirm this is the genuine app (a modified copy, or not signed with PreCut's keys). */
    data object AppNotVerified : AiFailure

    /** Google Play couldn't confirm the phone (an emulator, a rooted or uncertified phone). */
    data object DeviceNotVerified : AiFailure

    data class QuotaUsed(val secondsLeft: Int, val resetsAtMs: Long?) : AiFailure

    /** This month's budget for everyone is used up. */
    data class BudgetReached(val resetsAtMs: Long?) : AiFailure

    data object Paused : AiFailure

    data class TooLong(val maxSeconds: Int) : AiFailure

    data object TooLarge : AiFailure

    data object BadAudio : AiFailure

    /** The server speaks a newer version of the API than this app. */
    data object UpdateApp : AiFailure

    /** No usable answer (a timeout, an outage, the provider failing): worth trying again later. */
    data object Busy : AiFailure
}

/** A cloud AI request's outcome. */
sealed interface AiResult<out T> {
    data class Ok<T>(val value: T) : AiResult<T>
    data class Failed(val failure: AiFailure) : AiResult<Nothing>
}
