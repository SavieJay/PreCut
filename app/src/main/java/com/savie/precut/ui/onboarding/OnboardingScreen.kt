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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.ui.theme.afterglowBackground

/**
 * First-run onboarding (Phase 24, designs O-01 to O-05): Welcome, What will you create?, Your
 * editing experience, (Android 13+) the notifications prompt and the Pro trial offer. Skip on
 * every page (✕ on the trial); [onDone] goes to Projects (and on to the paywall after Start free
 * trial). Pages fill the screen and the top bar sits over them, so the welcome wall runs behind it.
 */
@Composable
fun OnboardingScreen(onDone: (openPaywall: Boolean) -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone(state.openPaywall) }
    BackHandler(enabled = state.page != OnboardingPage.WELCOME) { viewModel.onBack() }
    val notifications = rememberLauncherForActivityResult(RequestPermission()) { viewModel.onNotificationsAnswered() }
    // A moment on first run while it checks for existing projects: just the app's background.
    if (state.checking || state.done) {
        Box(Modifier.fillMaxSize().afterglowBackground())
        return
    }
    Box(Modifier.fillMaxSize().afterglowBackground()) {
        AnimatedContent(state.page, Modifier.fillMaxSize(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page") { page ->
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
        OnboardingTopBar(
            current = state.pages.indexOf(state.page),
            count = state.pages.size,
            close = state.page == OnboardingPage.PRO,
            lightSkip = state.page == OnboardingPage.WELCOME,
            onSkip = viewModel::onSkip,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        )
    }
}
