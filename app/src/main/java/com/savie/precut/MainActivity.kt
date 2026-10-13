package com.savie.precut

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark-only, so always use light system bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A recreated activity has already handled its launch intent.
        if (savedInstanceState == null) receiveShare(intent)
        setContent {
            PreCutTheme {
                AppNavHost(shareInbox)
            }
        }
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
