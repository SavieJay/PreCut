package com.savie.precut.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.glassRow
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** The top bar's height; pages leave this much room under the status bar. */
internal val OnboardingBarHeight = 56.dp

/** Onboarding's page titles (RD-O02 to RD-O05): Display, 34/40, 800, tight. */
@Composable
internal fun onboardingTitleStyle(): TextStyle = MaterialTheme.typography.displaySmall

/**
 * A page's title and subtitle over its [content] (scrolling on short phones), with the [bottom]
 * buttons pinned below, clear of the top bar and the system bars.
 */
@Composable
internal fun OnboardingPageLayout(
    title: String,
    subtitle: String?,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = OnboardingBarHeight).navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp),
        ) {
            Text(title, style = onboardingTitleStyle())
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.padding(top = 10.dp)) }
            Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = bottom,
        )
    }
}

/** RD-O02/O03: a picked card has a soft pink wash and a 2 dp pink ring; others are quiet glass rows. */
internal fun Modifier.choiceCard(selected: Boolean, shape: RoundedCornerShape = Radius.card): Modifier =
    if (selected) background(PickedWash, shape).border(2.dp, Accent, shape) else glassRow(shape)

private val PickedWash = Brush.linearGradient(listOf(Accent.copy(alpha = 0.18f), Accent.copy(alpha = 0.04f)))

/**
 * Skip (or ✕ on the Pro trial, [close]) and the page dots. [lightSkip] for the welcome page,
 * where Skip sits over pictures.
 */
@Composable
internal fun OnboardingTopBar(current: Int, count: Int, close: Boolean, lightSkip: Boolean, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(OnboardingBarHeight).padding(start = if (close) 4.dp else 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (close) PreCutIconButton(R.drawable.ic_close, stringResource(R.string.onboarding_close), onSkip)
        PageDots(current, count)
        Spacer(Modifier.weight(1f))
        if (!close) {
            AccentTextButton(stringResource(R.string.onboarding_skip), onSkip, color = if (lightSkip) TextPrimary else TextSecondary)
        }
    }
}

/** RD-O01 to O05: one dot per page; done pages in soft pink, the current one a 24 dp pink pill. */
@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_step, current + 1, count)
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo((current + 1).toFloat(), 1f..count.toFloat(), steps = (count - 2).coerceAtLeast(0))
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val color = when {
                index == current -> Accent
                index < current -> Accent.copy(alpha = 0.55f)
                else -> Color.White.copy(alpha = 0.2f)
            }
            Box(Modifier.width(if (index == current) 24.dp else 8.dp).height(8.dp).background(color, RoundedCornerShape(4.dp)))
        }
    }
}
