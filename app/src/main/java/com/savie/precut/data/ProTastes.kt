package com.savie.precut.data

import android.content.Context
import androidx.core.content.edit
import com.savie.precut.model.pro.ProFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Free tastes of Pro features run on the phone (Phase 26B): how many times each was used this
 * month (UTC), kept on the phone. Clearing the app's data gives them back; that's an accepted cost
 * for features that cost nothing to run.
 */
@Singleton
class ProTastes @Inject constructor(@ApplicationContext context: Context, private val clock: Clock) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun used(feature: ProFeature): Int = prefs.getInt(key(feature), 0)

    fun spend(feature: ProFeature) = prefs.edit { putInt(key(feature), used(feature) + 1) }

    private fun key(feature: ProFeature) = "${MONTH.format(clock.instant().atOffset(ZoneOffset.UTC))}:${feature.name}"

    private companion object {
        const val PREFS = "pro_tastes"
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}
