package com.savie.precut.ai

import com.savie.precut.model.ai.UploadSummary
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The user's yes to one upload: given only by the consent dialog's Send ([givenFor]), for the
 * [summary] the dialog showed, and spent by that upload. The cloud client sends nothing without
 * one, nor with one spent already or given for audio of another size, so no code path can upload
 * without asking first.
 */
class UploadConsent private constructor(val summary: UploadSummary) {
    private val spent = AtomicBoolean(false)

    /** Uses the consent for [bytes] of audio; false when it was used before or was given for other audio. */
    fun spendOn(bytes: Long): Boolean = bytes == summary.bytes && spent.compareAndSet(false, true)

    companion object {
        /** Only for the consent dialog's Send button. */
        fun givenFor(summary: UploadSummary) = UploadConsent(summary)
    }
}
