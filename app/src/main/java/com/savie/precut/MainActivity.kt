package com.savie.precut

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.savie.precut.auth.AccountRepository
import com.savie.precut.data.AppSettings
import com.savie.precut.data.sync.SyncRequests
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.savie.precut.model.account.AuthState
import com.savie.precut.data.InstallReferrerCheck
import com.savie.precut.data.TemplateLinkInbox
import com.savie.precut.model.template.TemplateLinks
import com.savie.precut.pro.EntitlementRepository
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var entitlements: EntitlementRepository

    @Inject
    lateinit var templateLinks: TemplateLinkInbox

    @Inject
    lateinit var installReferrers: InstallReferrerCheck

    @Inject
    lateinit var accounts: AccountRepository

    @Inject
    lateinit var sync: SyncRequests

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark-only, so always use light system bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A recreated activity has already handled its launch intent.
        if (savedInstanceState == null) {
            receive(intent)
            // First start after a Play install from a shared template's page: open that template.
            lifecycleScope.launch { installReferrers.runOnce() }
            // Pro follows the account (Phase 30): tie this install to it, once a launch.
            if (accounts.state.value is AuthState.SignedIn) accounts.linkInstall()
        }
        // Cloud projects (Phase 30C): sync at sign-in and each time the app comes to the front.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                accounts.state.map { it is AuthState.SignedIn }.distinctUntilChanged().collect { signedIn -> if (signedIn) sync.now() }
            }
        }
        // Read once: finishing onboarding mustn't swap the start screen under the user.
        val showOnboarding = !appSettings.values.value.onboardingDone
        setContent {
            PreCutTheme {
                CompositionLocalProvider(LocalUpdates provides Updates(updateChecker, playUpdates)) {
                    Box {
                        AppNavHost(
                            shareInbox,
                            showOnboarding,
                            templateLinks,
                            auth = accounts.state,
                            onboardingDone = { appSettings.values.value.onboardingDone },
                        )
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
        lifecycleScope.launch { entitlements.refreshIfStale() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    /** A template link (Phase 28), or media shared to PreCut. */
    private fun receive(intent: Intent) {
        val link = intent.data?.takeIf { intent.action == Intent.ACTION_VIEW }?.toString()
        val id = link?.let { TemplateLinks.idOf(it, BuildConfig.WEB_HOST) }
        if (id != null) templateLinks.offer(id) else receiveShare(intent)
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
