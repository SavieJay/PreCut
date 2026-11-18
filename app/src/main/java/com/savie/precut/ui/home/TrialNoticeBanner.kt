package com.savie.precut.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.savie.precut.R
import com.savie.precut.model.pro.trialDaysLeft
import com.savie.precut.model.pro.trialEndingSoon
import com.savie.precut.pro.EntitlementRepository
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class TrialNoticeViewModel @Inject constructor(entitlements: EntitlementRepository, clock: Clock) : ViewModel() {
    /** Days left when the trial ends within two days; null otherwise. */
    val daysLeft: StateFlow<Int?> = entitlements.known.map { known ->
        val now = clock.millis()
        if (trialEndingSoon(known.plan, now)) trialDaysLeft(known.plan, now) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Projects' note in the trial's last two days (Phase 31). */
@Composable
fun TrialNoticeBanner(viewModel: TrialNoticeViewModel = hiltViewModel()) {
    val days by viewModel.daysLeft.collectAsStateWithLifecycle()
    val left = days ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).glass(Radius.card).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_diamond), contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        Text(pluralStringResource(R.plurals.pro_trial_ending_banner, left, left), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
