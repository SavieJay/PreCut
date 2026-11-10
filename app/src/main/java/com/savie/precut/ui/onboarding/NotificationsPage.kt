package com.savie.precut.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Ink4
import com.savie.precut.ui.theme.TextSecondary

/**
 * Notifications priming (O-04, Android 13+): a bell over the export notification it's for, what
 * they're used for, Allow notifications (Android's own dialog) and Not now.
 */
@Composable
internal fun NotificationsPage(onAllow: () -> Unit, onNotNow: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = OnboardingBarHeight).navigationBarsPadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterVertically)) {
            NotificationPicture()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.onboarding_notifications_title), style = onboardingTitleStyle())
                Text(stringResource(R.string.onboarding_notifications_body), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = TextSecondary)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledButton(stringResource(R.string.onboarding_notifications_allow), onAllow, Modifier.fillMaxWidth(), large = true)
            AccentTextButton(stringResource(R.string.onboarding_not_now), onNotNow, Modifier.fillMaxWidth())
        }
    }
}

/** The bell, ringed by the page colour, overlapping what an export notification looks like. */
@Composable
private fun NotificationPicture() {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.zIndex(1f).size(116.dp).background(Ink1, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(96.dp).background(AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_notifications), contentDescription = null, tint = Accent, modifier = Modifier.size(44.dp))
            }
        }
        Column(
            Modifier.fillMaxWidth().offset(y = (-20).dp).shadow(16.dp, RoundedCornerShape(20.dp)).background(Ink2, RoundedCornerShape(20.dp))
                .padding(start = 16.dp, end = 16.dp, top = 30.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(24.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = Ink0, modifier = Modifier.size(16.dp))
                }
                Text(stringResource(R.string.onboarding_notifications_now, stringResource(R.string.app_name)), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            Text(stringResource(R.string.onboarding_notifications_mock), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            LinearProgressIndicator(
                progress = { 0.62f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Accent,
                trackColor = Ink4,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
