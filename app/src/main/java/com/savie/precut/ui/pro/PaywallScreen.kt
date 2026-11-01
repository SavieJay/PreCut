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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.model.pro.ProCatalog
import com.savie.precut.model.pro.ProFeature
import com.savie.precut.ui.cloud.aiFailureMessage
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextSecondary

/** The benefits listed on the paywall, in order. */
private val BENEFITS = listOf(
    R.drawable.ic_auto_awesome to R.string.pro_benefit_ai_edit,
    R.drawable.ic_splitscreen to R.string.pro_benefit_shorts,
    R.drawable.ic_text_fields to R.string.pro_benefit_transcript,
    R.drawable.ic_aspect_ratio to R.string.pro_benefit_formats,
    R.drawable.ic_closed_caption to R.string.pro_benefit_more_cloud,
)

/** The paywall (Phase 25, B1): never shown on its own, only from a tap on Pro or a Pro tool. */
@Composable
fun PaywallScreen(onClose: () -> Unit, viewModel: PaywallViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    // Pro confirmed by the server: done.
    LaunchedEffect(state.isPro) { if (state.isPro) onClose() }
    Column(Modifier.fillMaxSize().background(Ink0).systemBarsPadding()) {
        IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close)) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(64.dp).background(AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_diamond), contentDescription = null, tint = Accent, modifier = Modifier.size(32.dp))
            }
            Text(stringResource(headline(state.feature)), style = MaterialTheme.typography.headlineSmall)
            BENEFITS.forEach { (icon, text) -> BenefitRow(icon, text) }
            PlanCards(state, viewModel::onSelect)
            Text(stringResource(R.string.pro_free_forever), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.failure?.let { Text(aiFailureMessage(it), style = MaterialTheme.typography.bodySmall, color = Accent, textAlign = TextAlign.Center) }
            if (state.playFailed) Text(stringResource(R.string.pro_play_failed), style = MaterialTheme.typography.bodySmall, color = Accent, textAlign = TextAlign.Center)
            if (state.working) {
                CircularProgressIndicator(Modifier.size(32.dp), color = Accent)
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
            Text(stringResource(R.string.pro_fine_print), style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
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
private fun BenefitRow(@DrawableRes icon: Int, @StringRes text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}
