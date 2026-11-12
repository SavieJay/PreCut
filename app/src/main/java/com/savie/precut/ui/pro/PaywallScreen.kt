package com.savie.precut.ui.pro

import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.model.pro.ProCatalog
import com.savie.precut.model.pro.ProFeature
import com.savie.precut.ui.cloud.aiFailureMessage
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.components.PreCutSpinner
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.ErrorRed
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.afterglowBackground
import com.savie.precut.ui.theme.glass
import com.savie.precut.ui.theme.TextSecondary

/** The benefits listed on the paywall, in order. */
private val BENEFITS = listOf(
    Triple(R.drawable.ic_auto_awesome, R.string.pro_benefit_ai_edit, true),
    Triple(R.drawable.ic_splitscreen, R.string.pro_benefit_shorts, true),
    Triple(R.drawable.ic_text_fields, R.string.pro_benefit_transcript, true),
    Triple(R.drawable.ic_aspect_ratio, R.string.pro_benefit_formats, false),
    Triple(R.drawable.ic_closed_caption, R.string.pro_benefit_more_cloud, true),
)

private val HeroFill = Brush.linearGradient(listOf(Accent, Color(0xFFFF8FA3)))

/** The paywall (Phase 25, B1): never shown on its own, only from a tap on Pro or a Pro tool. */
@Composable
fun PaywallScreen(onClose: () -> Unit, viewModel: PaywallViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    // Pro confirmed by the server: done.
    LaunchedEffect(state.isPro) { if (state.isPro) onClose() }
    Column(Modifier.fillMaxSize().afterglowBackground().systemBarsPadding()) {
        PreCutIconButton(R.drawable.ic_close, stringResource(R.string.action_close), onClose, Modifier.padding(start = 4.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.padding(start = 4.dp).size(64.dp).background(HeroFill, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_diamond), contentDescription = null, tint = OnAccent, modifier = Modifier.size(32.dp))
            }
            Text(stringResource(headline(state.feature)), style = MaterialTheme.typography.headlineMedium, color = TextPrimary, modifier = Modifier.padding(horizontal = 4.dp))
            Column(Modifier.fillMaxWidth().glass(Radius.card).padding(horizontal = 18.dp, vertical = 8.dp)) {
                BENEFITS.forEach { (icon, text, ai) -> BenefitRow(icon, text, ai) }
            }
            PlanCards(state, viewModel::onSelect)
            Text(stringResource(R.string.pro_free_forever), style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(horizontal = 4.dp))
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.failure?.let { Text(aiFailureMessage(it), style = MaterialTheme.typography.bodySmall, color = ErrorRed, textAlign = TextAlign.Center) }
            if (state.playFailed) Text(stringResource(R.string.pro_play_failed), style = MaterialTheme.typography.bodySmall, color = ErrorRed, textAlign = TextAlign.Center)
            if (state.working) {
                PreCutSpinner()
            } else {
                val trial = state.offers?.let { if (state.selected == ProCatalog.YEARLY) it.yearly else it.monthly }?.freeTrialDays ?: 0
                FilledButton(
                    if (trial > 0) stringResource(R.string.pro_start_trial, trial) else stringResource(R.string.pro_continue),
                    { activity?.let(viewModel::onContinue) },
                    Modifier.fillMaxWidth(),
                    large = true,
                    enabled = state.offers?.isEmpty == false,
                )
            }
            AccentTextButton(stringResource(R.string.pro_restore), viewModel::onRestore, color = TextSecondary, enabled = !state.working)
            Text(stringResource(R.string.pro_fine_print), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = TextMuted, textAlign = TextAlign.Center)
        }
    }
}

@StringRes
private fun headline(feature: ProFeature?): Int = when (feature) {
    ProFeature.AI_EDIT -> R.string.pro_headline_ai_edit
    ProFeature.TRANSCRIPT_EDIT -> R.string.pro_headline_transcript
    ProFeature.LONG_TO_SHORTS -> R.string.pro_headline_shorts
    ProFeature.MULTI_FORMAT_EXPORT -> R.string.pro_headline_formats
    ProFeature.BRAND_KIT -> R.string.pro_headline_brand
    ProFeature.TRANSLATE_CAPTIONS -> R.string.pro_headline_translate
    ProFeature.HOOK_COACH -> R.string.pro_headline_hook
    ProFeature.SMART_BROLL -> R.string.pro_headline_broll
    null -> R.string.pro_headline
}

@Composable
private fun BenefitRow(@DrawableRes icon: Int, @StringRes text: Int, ai: Boolean) {
    // Lime marks the AI benefits only.
    Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = if (ai) AiAccent else TextSecondary, modifier = Modifier.size(20.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}
