package com.savie.precut.ui.onboarding

import com.savie.precut.data.InMemoryAppSettings
import com.savie.precut.data.SettingsValues
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.onboarding.CreationGoal
import com.savie.precut.model.onboarding.Experience
import com.savie.precut.pro.TrialOffers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Google Play offers a 7-day trial. */
    private val trial = TrialOffers { 7 }

    /** No trial: not from Play, offline, or already used. */
    private val noTrial = TrialOffers { 0 }

    @Test
    fun `answers set the ratio, the templates' order and simple mode`() {
        val settings = InMemoryAppSettings()
        val vm = OnboardingViewModel(settings, trial)
        vm.onNext()
        vm.onGoal(CreationGoal.YOUTUBE)
        vm.onNext()
        vm.onExperience(Experience.NEW)
        // Android 12 and below (and the JVM) have no notifications page: next is the Pro offer.
        vm.onNext()
        assertEquals(OnboardingPage.PRO, vm.state.value.page)
        vm.onSkip()
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
        val vm = OnboardingViewModel(settings, trial)
        vm.onSkip()
        val values = settings.values.value
        assertTrue(values.onboardingDone)
        assertEquals(AspectRatio.RATIO_4_5, values.aspectRatio)
        assertFalse(values.simpleMode)
    }

    @Test
    fun `back steps through the pages, and goals toggle`() {
        val vm = OnboardingViewModel(InMemoryAppSettings(), trial)
        assertFalse(vm.onBack())
        vm.onNext()
        vm.onGoal(CreationGoal.SOCIAL)
        vm.onGoal(CreationGoal.SOCIAL)
        assertTrue(vm.state.value.goals.isEmpty())
        assertTrue(vm.onBack())
        assertEquals(OnboardingPage.WELCOME, vm.state.value.page)
    }

    @Test
    fun `start free trial finishes onboarding and opens the paywall`() {
        val vm = OnboardingViewModel(InMemoryAppSettings(), trial)
        vm.onStartTrial()
        assertTrue(vm.state.value.done)
        assertTrue(vm.state.value.openPaywall)
    }

    @Test
    fun `the notifications page shows from Android 13`() {
        assertEquals(OnboardingPage.entries, pagesFor(33, trialDays = 7))
        assertFalse(OnboardingPage.NOTIFICATIONS in pagesFor(32, trialDays = 7))
    }

    @Test
    fun `without a trial from Google Play there's no Pro page`() {
        assertFalse(OnboardingPage.PRO in pagesFor(33, trialDays = 0))
        val settings = InMemoryAppSettings()
        val vm = OnboardingViewModel(settings, noTrial)
        vm.onNext()
        vm.onNext()
        assertEquals(OnboardingPage.EXPERIENCE, vm.state.value.page)
        // The last page (no notifications page on the JVM) finishes onboarding.
        vm.onNext()
        assertTrue(vm.state.value.done)
        assertFalse(vm.state.value.openPaywall)
        assertTrue(settings.values.value.onboardingDone)
    }

    @Test
    fun `the trial page carries Google Play's days`() {
        val vm = OnboardingViewModel(InMemoryAppSettings(), TrialOffers { 14 })
        assertEquals(14, vm.state.value.trialDays)
    }

    @Test
    fun `replaying the intro starts from the earlier answers`() {
        val vm = OnboardingViewModel(InMemoryAppSettings(SettingsValues(onboardingDone = true, simpleMode = true, creationGoals = setOf(CreationGoal.BUSINESS))), trial)
        assertEquals(setOf(CreationGoal.BUSINESS), vm.state.value.goals)
        assertEquals(Experience.NEW, vm.state.value.experience)
    }
}
