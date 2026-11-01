package com.savie.precut.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 23's Play flows against Play's own fake (it needs an Android context, so it runs on a
 * phone or emulator: `./gradlew connectedDebugAndroidTest`). The decision of when to block is
 * covered on the JVM by `UpdateRulesTest`.
 */
@RunWith(AndroidJUnit4::class)
class PlayUpdatesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fake = FakeAppUpdateManager(context)

    /** The fake shows its flows itself; the launcher is never used. */
    private val launcher = object : ActivityResultLauncher<IntentSenderRequest>() {
        override val contract: ActivityResultContract<IntentSenderRequest, *> = StartIntentSenderForResult()
        override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) = Unit
        override fun unregister() = Unit
    }

    private fun updates(installer: String?) = PlayUpdates(context, { fake }, { installer })

    @Test
    fun requiredUpdateStartsPlaysImmediateFlowAndInstalls() = runBlocking {
        fake.setUpdateAvailable(99, AppUpdateType.IMMEDIATE)
        assertTrue(updates(PlayUpdates.PLAY_STORE).start(AppUpdateType.IMMEDIATE, launcher))
        assertTrue(fake.isImmediateFlowVisible)
        fake.userAcceptsUpdate()
        fake.downloadStarts()
        fake.downloadCompletes()
        fake.installCompletes()
    }

    @Test
    fun bannerStartsTheFlexibleFlowAndRestartInstallsTheDownload() = runBlocking {
        fake.setUpdateAvailable(99, AppUpdateType.FLEXIBLE)
        val updates = updates(PlayUpdates.PLAY_STORE)
        assertTrue(updates.start(AppUpdateType.FLEXIBLE, launcher))
        assertTrue(fake.isConfirmationDialogVisible)
        fake.userAcceptsUpdate()
        fake.downloadStarts()
        fake.downloadCompletes()
        assertTrue(updates.downloaded.value)
        updates.completeUpdate()
        assertTrue(fake.isInstallSplashScreenVisible)
    }

    @Test
    fun withoutPlayOrWithoutAnUpdateTheCallerOpensTheStore() = runBlocking {
        fake.setUpdateAvailable(99, AppUpdateType.IMMEDIATE)
        assertFalse(updates(installer = "com.example.sideload").start(AppUpdateType.IMMEDIATE, launcher))
        fake.setUpdateNotAvailable()
        assertFalse(updates(PlayUpdates.PLAY_STORE).start(AppUpdateType.IMMEDIATE, launcher))
    }
}
