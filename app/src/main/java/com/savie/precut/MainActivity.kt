package com.savie.precut

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import com.savie.precut.data.AppSettings
import com.savie.precut.ui.update.LocalUpdates
import com.savie.precut.ui.update.UpdateHost
import com.savie.precut.ui.update.Updates
import com.savie.precut.update.PlayUpdates
import com.savie.precut.update.UpdateChecker
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.savie.precut.data.ShareInbox
import com.savie.precut.ui.navigation.AppNavHost
import com.savie.precut.ui.theme.PreCutTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shareInbox: ShareInbox

    @Inject
    lateinit var updateChecker: UpdateChecker

    @Inject
    lateinit var playUpdates: PlayUpdates

    @Inject
    lateinit var appSettings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark-only, so always use light system bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A recreated activity has already handled its launch intent.
        if (savedInstanceState == null) receiveShare(intent)
        // Read once: finishing onboarding mustn't swap the start screen under the user.
        val showOnboarding = !appSettings.values.value.onboardingDone
        setContent {
            PreCutTheme {
                CompositionLocalProvider(LocalUpdates provides Updates(updateChecker, playUpdates)) {
                    Box {
                        AppNavHost(shareInbox, showOnboarding)
                        // Drawn last, over everything: the forced-update screen and What's new.
                        UpdateHost(updateChecker, playUpdates)
                    }
                }
            }
        }
    }

    /** A cold start or a return to the app: check for updates (throttled). */
    override fun onStart() {
        super.onStart()
        updateChecker.onForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveShare(intent)
    }

    /** "Share to PreCut": queue the shared videos/photos for the Projects screen. */
    private fun receiveShare(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        shareInbox.offer(uris.map(Uri::toString))
    }
}
