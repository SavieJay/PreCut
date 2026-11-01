package com.savie.precut.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import com.savie.precut.model.onboarding.CreationGoal
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent

@DrawableRes
private fun CreationGoal.icon(): Int = when (this) {
    CreationGoal.SOCIAL -> R.drawable.ic_add_reaction
    CreationGoal.YOUTUBE -> R.drawable.ic_play_circle
    CreationGoal.MEMORIES -> R.drawable.ic_photo_camera
    CreationGoal.BUSINESS -> R.drawable.ic_ssid_chart
}

@StringRes
private fun CreationGoal.label(): Int = when (this) {
    CreationGoal.SOCIAL -> R.string.onboarding_goal_social
    CreationGoal.YOUTUBE -> R.string.onboarding_goal_youtube
    CreationGoal.MEMORIES -> R.string.onboarding_goal_memories
    CreationGoal.BUSINESS -> R.string.onboarding_goal_business
}

/** What will you create? (A2): a 2 × 2 grid of cards, any number picked; Continue needs one. */
@Composable
internal fun GoalsPage(goals: Set<CreationGoal>, onGoal: (CreationGoal) -> Unit, onContinue: () -> Unit) {
    OnboardingPageLayout(
        title = stringResource(R.string.onboarding_goals_title),
        subtitle = stringResource(R.string.onboarding_goals_subtitle),
        bottom = { FilledButton(stringResource(R.string.onboarding_continue), onContinue, Modifier.fillMaxWidth(), large = true, enabled = goals.isNotEmpty()) },
    ) {
        CreationGoal.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { goal -> GoalCard(goal, goal in goals, { onGoal(goal) }, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GoalCard(goal: CreationGoal, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ChoiceCard(selected, onClick, modifier.aspectRatio(1f)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(goal.icon())
                Text(stringResource(goal.label()), style = MaterialTheme.typography.titleSmall)
            }
            if (selected) {
                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = Accent, modifier = Modifier.align(Alignment.TopEnd).size(24.dp))
            }
        }
    }
}
