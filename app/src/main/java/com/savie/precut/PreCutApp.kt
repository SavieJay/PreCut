package com.savie.precut

import android.app.Application
import com.savie.precut.diagnostics.CrashLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PreCutApp : Application() {
    override fun onCreate() {
        // First, so a crash anywhere after this is recorded.
        CrashLog.install(this)
        super.onCreate()
    }
}
