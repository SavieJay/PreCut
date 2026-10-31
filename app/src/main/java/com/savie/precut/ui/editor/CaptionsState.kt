package com.savie.precut.ui.editor

import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.CloudStatus
import com.savie.precut.model.ai.UploadSummary
import com.savie.precut.model.text.CaptionStyle

/** Languages offered for captions (2-letter codes), after Auto (null). */
val CAPTION_LANGUAGES: List<String?> = listOf(null, "en", "yo", "ig", "ha", "fr", "ar", "pt", "es")

/** The free minutes, as the Captions sheet knows them. */
sealed interface CaptionQuota {
    data object Checking : CaptionQuota

    /** Nothing sent from this phone yet, so all of the month's free minutes are there. */
    data object NotUsed : CaptionQuota

    data class Known(val status: CloudStatus) : CaptionQuota

    /** The minutes couldn't be read: cloud AI is off, offline, not set up… */
    data class Unavailable(val failure: AiFailure) : CaptionQuota
}

/** Why captions couldn't be made, or can't be now. */
sealed interface CaptionFailure {
    data class Cloud(val failure: AiFailure) : CaptionFailure

    /** Every video is muted and there's no voiceover. */
    data object NoSound : CaptionFailure

    /** The sound couldn't be exported or read. */
    data object SoundNotReadable : CaptionFailure

    /** The sound was sent, but no words were heard. */
    data object NoSpeech : CaptionFailure

    /** One line was sent again (Phase 21B), but no words were heard in its time. */
    data object NoSpeechInLine : CaptionFailure
}

/** Automatic captions in the editor (Phase 21A), from the sheet to the result. */
sealed interface CaptionsUi {
    data object Closed : CaptionsUi

    /** The Captions sheet. */
    data class Options(
        /** A 2-letter code; null lets the provider tell. */
        val language: String?,
        val style: CaptionStyle,
        val durationMs: Long,
        /** What the whole timeline would cost, in seconds. */
        val neededSeconds: Int,
        /** What the first upload would cost; less than that left and nothing can be sent. */
        val firstPartSeconds: Int,
        /** How far into the timeline the free minutes reach ([durationMs] when they reach the end). */
        val reachMs: Long,
        val quota: CaptionQuota,
        /** Captions already in the project, which Generate replaces. */
        val replacing: Int,
        val hasSound: Boolean,
    ) : CaptionsUi {
        /** Why Generate can't run now; null when it can. */
        val blocker: CaptionFailure?
            get() = when {
                !hasSound -> CaptionFailure.NoSound
                quota is CaptionQuota.Unavailable -> CaptionFailure.Cloud(quota.failure)
                quota is CaptionQuota.Known && !quota.status.cloudEnabled -> CaptionFailure.Cloud(AiFailure.BudgetReached(quota.status.resetsAtMs))
                quota is CaptionQuota.Known && quota.status.secondsLeft < firstPartSeconds ->
                    CaptionFailure.Cloud(AiFailure.QuotaUsed(quota.status.secondsLeft, quota.status.resetsAtMs))
                else -> null
            }

        /** The minutes left reach only part of the timeline. */
        val minutesShort: Boolean get() = blocker == null && reachMs < durationMs

        val canGenerate: Boolean get() = blocker == null && quota != CaptionQuota.Checking
    }

    /** The sound is being exported and cut into parts. */
    data class Preparing(val percent: Int) : CaptionsUi

    /** Asking before the upload; [coversAll] is false when the free minutes reach only part of the timeline. */
    data class Consent(val summary: UploadSummary, val coversAll: Boolean) : CaptionsUi

    /** Part [part] (from 0) of [parts] is being sent; [stopping] once Stop was tapped. */
    data class Sending(val part: Int, val parts: Int, val stopping: Boolean = false) : CaptionsUi

    data class Failed(val failure: CaptionFailure) : CaptionsUi

    /**
     * [added] captions cover the timeline up to [coveredMs] of [durationMs]; [failure] ended the
     * job early, or the user stopped it (null).
     */
    data class Partial(val added: Int, val coveredMs: Long, val durationMs: Long, val failure: AiFailure?) : CaptionsUi
}
