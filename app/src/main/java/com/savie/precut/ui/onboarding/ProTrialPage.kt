package com.savie.precut.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
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
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.glass
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** What Pro adds; `true` marks the AI ones, which take the lime icon. */
private val TRIAL_BENEFITS = listOf(
    Triple(R.drawable.ic_auto_awesome, R.string.pro_benefit_ai_edit, true),
    Triple(R.drawable.ic_splitscreen, R.string.pro_benefit_shorts, true),
    Triple(R.drawable.ic_text_fields, R.string.pro_benefit_transcript, true),
    Triple(R.drawable.ic_aspect_ratio, R.string.pro_benefit_formats, false),
    Triple(R.drawable.ic_closed_caption, R.string.pro_benefit_more_cloud, true),
)

private val ProBadgeFill = Brush.linearGradient(listOf(Accent, Color(0xFFFF8FA3)))
private val CardDivider = Color.White.copy(alpha = 0.08f)

/**
 * The Pro trial offer (RD-O05): soft, never pushy, and only when Google Play offers this user a
 * trial ([trialDays], from Play). What stays free comes first, on a glass card with what Pro adds;
 * ✕ (top bar) and Continue free are both there; Start free trial shows the plans with Play's
 * prices on the paywall.
 */
@Composable
internal fun ProTrialPage(trialDays: Int, onStartTrial: () -> Unit, onContinueFree: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = OnboardingBarHeight).navigationBarsPadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp)) {
            Row(
                Modifier.padding(start = 20.dp).height(30.dp).background(ProBadgeFill, RoundedCornerShape(15.dp)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(painterResource(R.drawable.ic_diamond), contentDescription = null, tint = OnAccent, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.pro_badge),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp),
                    color = OnAccent,
                )
            }
            Text(
                pluralStringResource(R.plurals.onboarding_pro_title, trialDays, trialDays),
                style = onboardingTitleStyle(),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            )
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Benefit(R.drawable.ic_check, TextPrimary) {
                    Text(stringResource(R.string.pro_free_forever), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(CardDivider))
                TRIAL_BENEFITS.forEach { (icon, text, ai) ->
                    Benefit(icon, if (ai) AiAccent else TextSecondary) {
                        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledButton(stringResource(R.string.onboarding_pro_start), onStartTrial, Modifier.fillMaxWidth(), large = true)
            AccentTextButton(stringResource(R.string.onboarding_pro_continue_free), onContinueFree, color = TextSecondary)
            Text(
                stringResource(R.string.pro_fine_print),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
        }
    }
}

/** A line on the trial card: a 20 dp icon and its words. */
@Composable
private fun Benefit(@DrawableRes icon: Int, tint: Color, text: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(20.dp))
        text()
    }
}
