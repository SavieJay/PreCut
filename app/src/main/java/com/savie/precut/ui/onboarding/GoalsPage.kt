package com.savie.precut.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.onboarding.CreationGoal
import com.savie.precut.model.onboarding.defaultAspectRatio
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.editor.tools.label
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentFill
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

private val PreviewWell = Color.Black.copy(alpha = 0.3f)

@StringRes
private fun CreationGoal.label(): Int = when (this) {
    CreationGoal.SOCIAL -> R.string.onboarding_goal_social
    CreationGoal.YOUTUBE -> R.string.onboarding_goal_youtube
    CreationGoal.MEMORIES -> R.string.onboarding_goal_memories
    CreationGoal.BUSINESS -> R.string.onboarding_goal_business
}

private fun CreationGoal.scene(): ClipScene = when (this) {
    CreationGoal.SOCIAL -> ClipScene.CITY
    CreationGoal.YOUTUBE -> ClipScene.HIKE
    CreationGoal.MEMORIES -> ClipScene.BEACH
    CreationGoal.BUSINESS -> ClipScene.CAFE
}

/** The shape new projects start in when this is the only goal picked (the same rule as onboarding's answer). */
private fun CreationGoal.ratio(): AspectRatio = defaultAspectRatio(setOf(this)) ?: AspectRatio.RATIO_9_16

/**
 * What will you create? (O-02): a 2 × 2 grid of cards, any number picked, each showing the shape
 * it gives new projects; a line under the grid says what the picks add up to. Continue needs one.
 */
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
        defaultAspectRatio(goals)?.let { ratio ->
            Row(Modifier.padding(start = 4.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(R.drawable.ic_ratio), contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.onboarding_goals_summary, ratio.label()), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun GoalCard(goal: CreationGoal, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = Radius.card
    Box(modifier.clip(shape).choiceCard(selected, shape).toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(128.dp).background(PreviewWell, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                RatioFrame(goal.ratio(), goal.scene())
            }
            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(goal.label()), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold), color = TextPrimary)
                Text(goal.ratio().label(), style = PreCutType.timecode.copy(fontSize = 11.sp, lineHeight = 20.sp), color = TextMuted)
            }
        }
        CheckMark(selected, Modifier.align(Alignment.TopEnd).padding(12.dp))
    }
}

/** The goal's shape, filled with stand-in footage: upright, wide or square, at most 132 × 107 dp. */
@Composable
private fun RatioFrame(ratio: AspectRatio, scene: ClipScene) {
    val r = ratio.widthRatio.toFloat() / ratio.heightRatio
    val (width, height) = when {
        r > 1f -> 132f to 132f / r
        r < 1f -> 107f * r to 107f
        else -> 92f to 92f
    }
    Box(Modifier.size(width.dp, height.dp).clip(RoundedCornerShape(6.dp)).background(scene.brush).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(6.dp)))
}

/** Picked: a filled accent circle with a tick; not picked: an empty ring. */
@Composable
private fun CheckMark(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Box(modifier.size(26.dp).background(AccentFill, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = OnAccent, modifier = Modifier.size(18.dp))
        }
    } else {
        Box(modifier.size(26.dp).border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape))
    }
}
