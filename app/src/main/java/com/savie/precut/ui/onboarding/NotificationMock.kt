package com.savie.precut.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.PreCutProgressBar
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.GlassLevel
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glass

/** RD-O04's picture of an export notification: raised glass (24 dp corners), the app line, the text and a pink bar. */
@Composable
internal fun ExportNotificationMock(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clearAndSetSemantics {}
            .glass(RoundedCornerShape(24.dp), GlassLevel.Raised)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(22.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = Ink0, modifier = Modifier.size(14.dp))
            }
            Text(
                stringResource(R.string.onboarding_notifications_now, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Text(stringResource(R.string.onboarding_notifications_mock), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
        PreCutProgressBar(0.62f, height = 6.dp)
    }
}
