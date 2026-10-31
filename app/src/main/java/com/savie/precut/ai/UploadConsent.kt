package com.savie.precut.ai

import com.savie.precut.model.ai.UploadSummary
import java.util.concurrent.atomic.AtomicInteger

/**
 * The user's yes to one job's uploads: given only by the consent dialog's Send ([givenFor]), for
 * the [summary] the dialog showed. Each upload spends the next of [UploadSummary.uploads], in
 * order and of exactly that size, so a job sends no more than was shown, and no code path can
 * upload without asking first.
 */
class UploadConsent private constructor(val summary: UploadSummary) {
    private val spent = AtomicInteger(0)

    /** Uses the consent for the next upload, of [bytes]; false when all were used or the next is another size. */
    fun spendOn(bytes: Long): Boolean {
        while (true) {
            val index = spent.get()
            if (summary.uploads.getOrNull(index) != bytes) return false
            if (spent.compareAndSet(index, index + 1)) return true
        }
    }

    companion object {
        /** Only for the consent dialog's Send button. */
        fun givenFor(summary: UploadSummary) = UploadConsent(summary)
    }
}
