package com.savie.precut.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.TextSecondary

/** Notifications priming (A4, Android 13+): what they're for, with a mock export notification. */
@Composable
internal fun NotificationsPage(onAllow: () -> Unit, onNotNow: () -> Unit) {
    OnboardingPageLayout(
        title = stringResource(R.string.onboarding_notifications_title),
        subtitle = stringResource(R.string.onboarding_notifications_body),
        bottom = {
            FilledButton(stringResource(R.string.onboarding_notifications_allow), onAllow, Modifier.fillMaxWidth(), large = true)
            AccentTextButton(stringResource(R.string.onboarding_not_now), onNotNow, color = TextSecondary)
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            IconBadge(R.drawable.ic_notifications, size = 88)
            // What an export notification looks like.
            Column(Modifier.fillMaxWidth().background(Ink2, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                Text(stringResource(R.string.onboarding_notifications_mock), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { 0.62f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Accent,
                    trackColor = Ink3,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}
