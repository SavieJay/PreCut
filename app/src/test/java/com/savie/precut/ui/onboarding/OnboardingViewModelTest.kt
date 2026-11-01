package com.savie.precut.ui.onboarding

import com.savie.precut.data.InMemoryAppSettings
import com.savie.precut.data.SettingsValues
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.onboarding.CreationGoal
import com.savie.precut.model.onboarding.Experience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {

    @Test
    fun `answers set the ratio, the templates' order and simple mode`() {
        val settings = InMemoryAppSettings()
        val vm = OnboardingViewModel(settings)
        vm.onNext()
        vm.onGoal(CreationGoal.YOUTUBE)
        vm.onNext()
        vm.onExperience(Experience.NEW)
        // Android 12 and below (and the JVM) have no notifications page: this finishes.
        vm.onNext()
        val values = settings.values.value
        assertTrue(vm.state.value.done)
        assertTrue(values.onboardingDone)
        assertEquals(setOf(CreationGoal.YOUTUBE), values.creationGoals)
        assertEquals(AspectRatio.RATIO_16_9, values.aspectRatio)
        assertTrue(values.simpleMode)
    }

    @Test
    fun `skip keeps the defaults for what wasn't answered`() {
        val settings = InMemoryAppSettings(SettingsValues(aspectRatio = AspectRatio.RATIO_4_5))
        val vm = OnboardingViewModel(settings)
        vm.onSkip()
        val values = settings.values.value
        assertTrue(values.onboardingDone)
        assertEquals(AspectRatio.RATIO_4_5, values.aspectRatio)
        assertFalse(values.simpleMode)
    }

    @Test
    fun `back steps through the pages, and goals toggle`() {
        val vm = OnboardingViewModel(InMemoryAppSettings())
        assertFalse(vm.onBack())
        vm.onNext()
        vm.onGoal(CreationGoal.SOCIAL)
        vm.onGoal(CreationGoal.SOCIAL)
        assertTrue(vm.state.value.goals.isEmpty())
        assertTrue(vm.onBack())
        assertEquals(OnboardingPage.WELCOME, vm.state.value.page)
    }

    @Test
    fun `the notifications page shows from Android 13`() {
        assertEquals(OnboardingPage.entries, pagesFor(33))
        assertFalse(OnboardingPage.NOTIFICATIONS in pagesFor(32))
    }

    @Test
    fun `replaying the intro starts from the earlier answers`() {
        val vm = OnboardingViewModel(InMemoryAppSettings(SettingsValues(onboardingDone = true, simpleMode = true, creationGoals = setOf(CreationGoal.BUSINESS))))
        assertEquals(setOf(CreationGoal.BUSINESS), vm.state.value.goals)
        assertEquals(Experience.NEW, vm.state.value.experience)
    }
}
