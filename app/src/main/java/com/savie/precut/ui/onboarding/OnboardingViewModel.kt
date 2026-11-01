package com.savie.precut.ui.onboarding

import android.os.Build
import androidx.lifecycle.ViewModel
import com.savie.precut.data.AppSettings
import com.savie.precut.model.onboarding.CreationGoal
import com.savie.precut.model.onboarding.Experience
import com.savie.precut.model.onboarding.defaultAspectRatio
import com.savie.precut.model.onboarding.simpleModeFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Onboarding's pages, in order. Notifications only where Android asks (13 and newer). */
enum class OnboardingPage { WELCOME, GOALS, EXPERIENCE, NOTIFICATIONS }

data class OnboardingState(
    val page: OnboardingPage = OnboardingPage.WELCOME,
    val goals: Set<CreationGoal> = emptySet(),
    val experience: Experience? = null,
    val done: Boolean = false,
) {
    val pages: List<OnboardingPage> get() = pagesFor(Build.VERSION.SDK_INT)
}

/** The pages on Android [sdk]: below 13 there's no notifications prompt to prepare for. */
fun pagesFor(sdk: Int): List<OnboardingPage> =
    OnboardingPage.entries.filter { it != OnboardingPage.NOTIFICATIONS || sdk >= Build.VERSION_CODES.TIRAMISU }

/**
 * First-run onboarding (Phase 24): a few skippable pages whose answers set the default aspect
 * ratio, the templates shown first and Simple or Full toolbar. Skip on any page keeps what was
 * chosen so far and sensible defaults for the rest.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: AppSettings) : ViewModel() {
    private val _state = MutableStateFlow(
        settings.values.value.let { values ->
            // Replaying the intro starts from the earlier answers.
            val experience = if (!values.onboardingDone) null else if (values.simpleMode) Experience.NEW else Experience.SOME
            OnboardingState(goals = values.creationGoals, experience = experience)
        },
    )
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun onGoal(goal: CreationGoal) = _state.update { it.copy(goals = if (goal in it.goals) it.goals - goal else it.goals + goal) }

    fun onExperience(experience: Experience) = _state.update { it.copy(experience = experience) }

    fun onNext() {
        val state = _state.value
        val next = state.pages.getOrNull(state.pages.indexOf(state.page) + 1)
        if (next == null) finish() else _state.update { it.copy(page = next) }
    }

    fun onBack(): Boolean {
        val state = _state.value
        val previous = state.pages.getOrNull(state.pages.indexOf(state.page) - 1) ?: return false
        _state.update { it.copy(page = previous) }
        return true
    }

    /** The notifications prompt was answered (either way): the export screen won't ask again. */
    fun onNotificationsAnswered() {
        settings.update { it.copy(notificationsAsked = true) }
        onNext()
    }

    fun onSkip() = finish()

    private fun finish() {
        val state = _state.value
        settings.update { values ->
            values.copy(
                onboardingDone = true,
                creationGoals = state.goals,
                aspectRatio = defaultAspectRatio(state.goals) ?: values.aspectRatio,
                simpleMode = state.experience?.let(::simpleModeFor) ?: values.simpleMode,
            )
        }
        _state.update { it.copy(done = true) }
    }
}
