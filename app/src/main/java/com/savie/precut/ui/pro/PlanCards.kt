package com.savie.precut.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.pro.PlanOffer
import com.savie.precut.model.pro.ProCatalog
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.AccentFill
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.Outline
import com.savie.precut.ui.theme.TextSecondary

/** Yearly (with "Best value") and Monthly, priced by Play; a note when Play has none to offer. */
@Composable
internal fun PlanCards(state: PaywallState, onSelect: (String) -> Unit) {
    val offers = state.offers
    when {
        offers == null -> CircularProgressIndicator(Modifier.padding(8.dp), color = Accent)
        offers.isEmpty -> Text(stringResource(R.string.pro_no_offers), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            offers.yearly?.let { PlanCard(it, state.selected == ProCatalog.YEARLY, offers.yearlySavingPercent, onSelect) }
            offers.monthly?.let { PlanCard(it, state.selected == ProCatalog.MONTHLY, null, onSelect) }
        }
    }
}

@Composable
private fun PlanCard(offer: PlanOffer, selected: Boolean, savingPercent: Int?, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val yearly = offer.basePlanId == ProCatalog.YEARLY
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) AccentContainer else Ink2, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Accent else Outline, shape)
            .clickable { onSelect(offer.basePlanId) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(if (yearly) R.string.pro_yearly else R.string.pro_monthly), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(if (yearly) R.string.pro_price_year else R.string.pro_price_month, offer.price),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            if (offer.freeTrialDays > 0) {
                Text(pluralStringResource(R.plurals.pro_trial_days, offer.freeTrialDays, offer.freeTrialDays), style = MaterialTheme.typography.bodySmall, color = Accent)
            }
        }
        if (yearly) {
            val tag = savingPercent?.let { stringResource(R.string.pro_save_percent, it) } ?: stringResource(R.string.pro_best_value)
            Text(
                tag,
                style = MaterialTheme.typography.labelMedium,
                color = OnAccent,
                modifier = Modifier.background(AccentFill, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
