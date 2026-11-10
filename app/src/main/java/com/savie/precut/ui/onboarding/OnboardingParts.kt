package com.savie.precut.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.SelectedSurface
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** The top bar's height; pages leave this much room under the status bar. */
internal val OnboardingBarHeight = 56.dp

/** Onboarding's page titles (O-02 to O-05): 28 sp, bold, a little tight. */
@Composable
internal fun onboardingTitleStyle(): TextStyle =
    MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)

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
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = TextSecondary, modifier = Modifier.padding(top = 8.dp)) }
            Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = bottom,
        )
    }
}

/** A picked card is tinted, with an accent edge; others sit plainly on the page. */
internal fun Modifier.choiceCard(selected: Boolean, shape: RoundedCornerShape = RoundedCornerShape(16.dp)): Modifier =
    background(if (selected) SelectedSurface else Ink1, shape).then(if (selected) Modifier.border(2.dp, Accent, shape) else Modifier)

/**
 * Skip (or ✕ on the Pro trial, [close]) and the progress track. [lightSkip] for the welcome
 * page, where Skip sits over pictures.
 */
@Composable
internal fun OnboardingTopBar(current: Int, count: Int, close: Boolean, lightSkip: Boolean, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(OnboardingBarHeight).padding(start = if (close) 4.dp else 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (close) {
            IconButton(onClick = onSkip) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.onboarding_close)) }
        }
        ClipTrackProgress(current, count, Modifier.width(200.dp))
        Spacer(Modifier.weight(1f))
        if (!close) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.onboarding_skip), style = MaterialTheme.typography.labelLarge, color = if (lightSkip) TextPrimary else TextSecondary)
            }
        }
    }
}

/**
 * The progress indicator as a little timeline: one clip per page, those done in the accent, the
 * current one part-filled with a white playhead, like the editor's.
 */
@Composable
private fun ClipTrackProgress(current: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_step, current + 1, count)
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo((current + 1).toFloat(), 1f..count.toFloat(), steps = (count - 2).coerceAtLeast(0))
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(Modifier.weight(1f).height(12.dp), contentAlignment = Alignment.CenterStart) {
                Segment(if (index < current) Accent else Ink3, Modifier.fillMaxWidth())
                if (index == current) {
                    Segment(Accent, Modifier.fillMaxWidth(CURRENT_FILL))
                    Box(Modifier.fillMaxWidth(CURRENT_FILL).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(TextPrimary, RoundedCornerShape(1.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun Segment(color: Color, modifier: Modifier) {
    Box(modifier.height(6.dp).background(color, RoundedCornerShape(3.dp)))
}

/** How far the current page's clip is filled. */
private const val CURRENT_FILL = 0.45f
