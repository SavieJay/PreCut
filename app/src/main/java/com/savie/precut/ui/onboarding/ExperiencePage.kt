package com.savie.precut.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.model.onboarding.Experience
import com.savie.precut.model.onboarding.simpleModeFor
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Outline
import com.savie.precut.ui.theme.SelectedSurface
import com.savie.precut.ui.theme.TextSecondary

private data class ExperienceOption(val experience: Experience, @StringRes val title: Int, @StringRes val body: Int)

private val OPTIONS = listOf(
    ExperienceOption(Experience.NEW, R.string.onboarding_experience_new, R.string.onboarding_experience_new_body),
    ExperienceOption(Experience.SOME, R.string.onboarding_experience_some, R.string.onboarding_experience_some_body),
    ExperienceOption(Experience.PRO, R.string.onboarding_experience_pro, R.string.onboarding_experience_pro_body),
)

/** The Simple toolbar as the editor shows it (EditorToolbar), More last. */
private val SIMPLE_TOOLS = listOf(
    R.drawable.ic_auto_awesome to R.string.tool_ai_edit, R.drawable.ic_split to R.string.tool_split, R.drawable.ic_trim to R.string.tool_trim,
    R.drawable.ic_music to R.string.tool_audio, R.drawable.ic_text to R.string.tool_text, R.drawable.ic_filter to R.string.tool_filters,
    R.drawable.ic_apps to R.string.tool_more,
)

/** The full toolbar's first tools; the rest scroll in from the right. */
private val FULL_TOOLS = listOf(
    R.drawable.ic_auto_awesome to R.string.tool_ai_edit, R.drawable.ic_split to R.string.tool_split, R.drawable.ic_crop to R.string.tool_edit,
    R.drawable.ic_trim to R.string.tool_trim, R.drawable.ic_music to R.string.tool_audio, R.drawable.ic_speed to R.string.tool_speed,
    R.drawable.ic_animation to R.string.tool_animation, R.drawable.ic_canvas to R.string.tool_canvas,
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
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).choiceCard(selected, shape).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            // The card is the radio's touch target; the radio only shows the state.
            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = Outline))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(option.title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp))
                Text(stringResource(option.body), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        if (selected) ToolbarPreview(simple = simpleModeFor(option.experience))
    }
}

/** A small copy of the toolbar this choice gives, with a line saying which. */
@Composable
private fun ToolbarPreview(simple: Boolean) {
    val caption = stringResource(if (simple) R.string.onboarding_experience_simple_preview else R.string.onboarding_experience_full_preview)
    Column(Modifier.clearAndSetSemantics { contentDescription = caption }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(caption, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(Ink0)) {
            Row(Modifier.matchParentSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                (if (simple) SIMPLE_TOOLS else FULL_TOOLS).forEachIndexed { index, (icon, label) ->
                    MiniTool(icon, label, highlighted = simple && index == SIMPLE_TOOLS.lastIndex, modifier = Modifier.weight(1f))
                }
            }
            // The full toolbar runs on past the edge.
            if (!simple) {
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0.7f to Color.Transparent, 1f to SelectedSurface)))
            }
        }
    }
}

@Composable
private fun MiniTool(@DrawableRes icon: Int, @StringRes label: Int, highlighted: Boolean, modifier: Modifier = Modifier) {
    val color = if (highlighted) Accent else Color(0xFFC9C9D0)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
