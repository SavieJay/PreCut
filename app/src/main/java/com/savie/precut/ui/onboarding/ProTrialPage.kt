package com.savie.precut.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.OnAccentContainer
import com.savie.precut.ui.theme.Success
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

private val TRIAL_BENEFITS = listOf(
    R.drawable.ic_auto_awesome to R.string.pro_benefit_ai_edit,
    R.drawable.ic_splitscreen to R.string.pro_benefit_shorts,
    R.drawable.ic_text_fields to R.string.pro_benefit_transcript,
    R.drawable.ic_aspect_ratio to R.string.pro_benefit_formats,
    R.drawable.ic_closed_caption to R.string.pro_benefit_more_cloud,
)

/**
 * The Pro trial offer (O-05): soft, never pushy, and only when Google Play offers this user a
 * trial ([trialDays], from Play). What stays free comes first; ✕ (top bar) and Continue free are
 * both there; Start free trial shows the plans with Play's prices on the paywall.
 */
@Composable
internal fun ProTrialPage(trialDays: Int, onStartTrial: () -> Unit, onContinueFree: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = OnboardingBarHeight).navigationBarsPadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp)) {
            Text(
                stringResource(R.string.pro_badge),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                color = OnAccentContainer,
                modifier = Modifier.background(AccentContainer, RoundedCornerShape(11.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(pluralStringResource(R.plurals.onboarding_pro_title, trialDays, trialDays), style = onboardingTitleStyle(), modifier = Modifier.padding(top = 12.dp))
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().background(Ink1, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = Success, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.pro_free_forever), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9C9D0))
            }
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TRIAL_BENEFITS.forEach { (icon, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(44.dp).background(Ink2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(icon), contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
                        }
                        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledButton(stringResource(R.string.onboarding_pro_start), onStartTrial, Modifier.fillMaxWidth(), large = true)
            AccentTextButton(stringResource(R.string.onboarding_pro_continue_free), onContinueFree, Modifier.fillMaxWidth(), color = TextPrimary)
            Text(
                stringResource(R.string.pro_fine_print),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
        }
    }
}
