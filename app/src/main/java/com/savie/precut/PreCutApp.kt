package com.savie.precut

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import javax.inject.Inject
import com.savie.precut.diagnostics.CrashLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PreCutApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** Workers (cloud sync, Phase 30) get their dependencies from Hilt. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        // First, so a crash anywhere after this is recorded.
        CrashLog.install(this)
        super.onCreate()
    }
}
