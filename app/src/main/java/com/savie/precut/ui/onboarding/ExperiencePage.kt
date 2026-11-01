package com.savie.precut.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.onboarding.Experience
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.TextSecondary

private data class ExperienceOption(val experience: Experience, val icon: Int, val title: Int, val body: Int)

private val OPTIONS = listOf(
    ExperienceOption(Experience.NEW, R.drawable.ic_lightbulb, R.string.onboarding_experience_new, R.string.onboarding_experience_new_body),
    ExperienceOption(Experience.SOME, R.drawable.ic_edit, R.string.onboarding_experience_some, R.string.onboarding_experience_some_body),
    ExperienceOption(Experience.PRO, R.drawable.ic_diamond, R.string.onboarding_experience_pro, R.string.onboarding_experience_pro_body),
)

/** Your editing experience (A3): New keeps the toolbar simple; the note says it can change later. */
@Composable
internal fun ExperiencePage(experience: Experience?, onExperience: (Experience) -> Unit, onContinue: () -> Unit) {
    OnboardingPageLayout(
        title = stringResource(R.string.onboarding_experience_title),
        subtitle = null,
        bottom = { FilledButton(stringResource(R.string.onboarding_continue), onContinue, Modifier.fillMaxWidth(), large = true, enabled = experience != null) },
    ) {
        OPTIONS.forEach { option ->
            ChoiceCard(option.experience == experience, { onExperience(option.experience) }, Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconBadge(option.icon)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(option.title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(option.body), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
        Text(stringResource(R.string.onboarding_experience_note), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
