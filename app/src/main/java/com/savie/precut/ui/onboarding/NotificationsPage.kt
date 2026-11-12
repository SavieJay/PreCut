package com.savie.precut.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

private val BellShape = RoundedCornerShape(44.dp)
private val BellGlow = Brush.radialGradient(listOf(Accent.copy(alpha = 0.34f), Accent.copy(alpha = 0.06f)))

/**
 * Notifications priming (RD-O04, Android 13+): a glowing bell, what an export notification looks
 * like, what they're used for, Allow notifications (Android's own dialog) and Not now.
 */
@Composable
internal fun NotificationsPage(onAllow: () -> Unit, onNotNow: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = OnboardingBarHeight).navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            Bell()
            ExportNotificationMock(Modifier.widthIn(max = 300.dp).fillMaxWidth())
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.onboarding_notifications_title), style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
                Text(stringResource(R.string.onboarding_notifications_body), style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledButton(stringResource(R.string.onboarding_notifications_allow), onAllow, Modifier.fillMaxWidth(), large = true)
            AccentTextButton(stringResource(R.string.onboarding_not_now), onNotNow, Modifier.fillMaxWidth(), color = TextSecondary)
        }
    }
}

/** A 132 dp pink squircle with a soft glow and the bell. */
@Composable
private fun Bell() {
    Box(
        Modifier
            .clearAndSetSemantics {}
            .size(132.dp)
            .shadow(40.dp, BellShape, ambientColor = Color.Transparent, spotColor = Accent)
            .background(Ink0, BellShape)
            .background(BellGlow, BellShape)
            .border(1.dp, Accent.copy(alpha = 0.45f), BellShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_notifications), contentDescription = null, tint = Accent, modifier = Modifier.size(48.dp))
    }
}
