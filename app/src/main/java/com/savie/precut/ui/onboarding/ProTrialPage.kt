package com.savie.precut.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.TextSecondary

private val TRIAL_BENEFITS = listOf(
    R.drawable.ic_auto_awesome to R.string.pro_benefit_ai_edit,
    R.drawable.ic_splitscreen to R.string.pro_benefit_shorts,
    R.drawable.ic_text_fields to R.string.pro_benefit_transcript,
    R.drawable.ic_aspect_ratio to R.string.pro_benefit_formats,
)

/**
 * The Pro trial offer (A5): soft, never pushy. Continue free is always there (and Skip at the top);
 * Start free trial shows the plans with Play's prices on the paywall.
 */
@Composable
internal fun ProTrialPage(onStartTrial: () -> Unit, onContinueFree: () -> Unit) {
    OnboardingPageLayout(
        title = stringResource(R.string.onboarding_pro_title),
        subtitle = stringResource(R.string.pro_free_forever),
        bottom = {
            FilledButton(stringResource(R.string.onboarding_pro_start), onStartTrial, Modifier.fillMaxWidth(), large = true)
            AccentTextButton(stringResource(R.string.onboarding_pro_continue_free), onContinueFree, color = TextSecondary)
            Text(stringResource(R.string.pro_fine_print), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        },
    ) {
        TRIAL_BENEFITS.forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(painterResource(icon), contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
                Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
