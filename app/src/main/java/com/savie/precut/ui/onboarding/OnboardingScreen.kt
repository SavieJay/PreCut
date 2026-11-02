package com.savie.precut.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Ink4
import com.savie.precut.ui.theme.TextSecondary

/**
 * First-run onboarding (Phase 24): Welcome, What will you create?, Your editing experience,
 * (Android 13+) the notifications prompt and the Pro trial offer. Skip on every page; [onDone]
 * goes to Projects (and on to the paywall after Start free trial).
 */
@Composable
fun OnboardingScreen(onDone: (openPaywall: Boolean) -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone(state.openPaywall) }
    BackHandler(enabled = state.page != OnboardingPage.WELCOME) { viewModel.onBack() }
    val notifications = rememberLauncherForActivityResult(RequestPermission()) { viewModel.onNotificationsAnswered() }
    Column(Modifier.fillMaxSize().background(Ink0).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressDots(state.pages.indexOf(state.page), state.pages.size, Modifier.weight(1f))
            AccentTextButton(stringResource(R.string.onboarding_skip), viewModel::onSkip, color = TextSecondary)
        }
        AnimatedContent(state.page, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page") { page ->
            when (page) {
                OnboardingPage.WELCOME -> WelcomePage(onStart = viewModel::onNext, onHaveProjects = viewModel::onSkip)
                OnboardingPage.GOALS -> GoalsPage(state.goals, viewModel::onGoal, viewModel::onNext)
                OnboardingPage.EXPERIENCE -> ExperiencePage(state.experience, viewModel::onExperience, viewModel::onNext)
                OnboardingPage.NOTIFICATIONS -> NotificationsPage(
                    // The page only shows on Android 13+, where the permission exists.
                    onAllow = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.onNext()
                    },
                    // Not now: the export screen can still ask later.
                    onNotNow = viewModel::onNext,
                )
                OnboardingPage.PRO -> ProTrialPage(state.trialDays, onStartTrial = viewModel::onStartTrial, onContinueFree = viewModel::onSkip)
            }
        }
    }
}

/** One dot per page, the current one in the accent colour. */
@Composable
private fun ProgressDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            Box(Modifier.size(8.dp).background(if (index == current) Accent else Ink4, CircleShape))
        }
    }
}
