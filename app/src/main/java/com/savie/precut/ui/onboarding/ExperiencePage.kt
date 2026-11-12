package com.savie.precut.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.onboarding.Experience
import com.savie.precut.model.onboarding.simpleModeFor
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

private data class ExperienceOption(val experience: Experience, @StringRes val title: Int, @StringRes val body: Int)

private val OPTIONS = listOf(
    ExperienceOption(Experience.NEW, R.string.onboarding_experience_new, R.string.onboarding_experience_new_body),
    ExperienceOption(Experience.SOME, R.string.onboarding_experience_some, R.string.onboarding_experience_some_body),
    ExperienceOption(Experience.PRO, R.string.onboarding_experience_pro, R.string.onboarding_experience_pro_body),
)

/**
 * Your editing experience (O-03): one choice; the picked card opens to show the toolbar it gives
 * (New to editing: Simple mode; the others: the full toolbar). The note says it can change later.
 */
@Composable
internal fun ExperiencePage(experience: Experience?, onExperience: (Experience) -> Unit, onContinue: () -> Unit) {
    OnboardingPageLayout(
        title = stringResource(R.string.onboarding_experience_title),
        subtitle = stringResource(R.string.onboarding_experience_subtitle),
        bottom = { FilledButton(stringResource(R.string.onboarding_continue), onContinue, Modifier.fillMaxWidth(), large = true, enabled = experience != null) },
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OPTIONS.forEach { option -> ExperienceCard(option, option.experience == experience) { onExperience(option.experience) } }
        }
        Text(stringResource(R.string.onboarding_experience_note), style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    }
}

@Composable
private fun ExperienceCard(option: ExperienceOption, selected: Boolean, onClick: () -> Unit) {
    val shape = Radius.card
    Column(
        Modifier.fillMaxWidth().clip(shape).choiceCard(selected, shape).selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(option.title), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(stringResource(option.body), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            // The card is the radio's touch target; the ring only shows the state.
            RadioRing(selected)
        }
        if (selected) ToolbarPreview(simple = simpleModeFor(option.experience))
    }
}

/** RD-O03's radio: a 2 dp white ring, filled to a thick pink ring when picked. */
@Composable
private fun RadioRing(selected: Boolean) {
    Box(
        Modifier.size(24.dp).then(
            if (selected) Modifier.border(7.dp, Accent, CircleShape) else Modifier.border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        ),
    )
}
