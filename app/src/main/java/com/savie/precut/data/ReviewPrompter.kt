package com.savie.precut.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory
import com.savie.precut.model.growth.shouldAskForReview
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play's in-app review (Phase 28), after a successful export only: counts good exports and asks
 * now and then ([shouldAskForReview]). Play decides whether the card really shows.
 */
@Singleton
class ReviewPrompter @Inject constructor(@ApplicationContext private val context: Context, private val clock: Clock) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val manager by lazy { ReviewManagerFactory.create(context) }

    /** An export just succeeded: maybe ask for a rating over [activity]. */
    suspend fun onExportDone(activity: Activity) {
        val exports = prefs.getInt(KEY_EXPORTS, 0) + 1
        prefs.edit { putInt(KEY_EXPORTS, exports) }
        val last = prefs.getLong(KEY_LAST, -1L).takeIf { it >= 0 }
        if (!shouldAskForReview(clock.millis(), last, exports)) return
        prefs.edit { putLong(KEY_LAST, clock.millis()) }
        try {
            val info = manager.requestReviewFlow().await()
            manager.launchReviewFlow(activity, info).await()
        } catch (e: RuntimeException) {
            // No Play Store, or Play declined: nothing to show, nothing to tell.
            Log.i(TAG, "No review card", e)
        }
    }

    private companion object {
        const val TAG = "ReviewPrompter"
        const val PREFS = "review"
        const val KEY_EXPORTS = "successfulExports"
        const val KEY_LAST = "lastAskedMs"
    }
}
