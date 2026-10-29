package com.reelwise.app

import com.reelwise.app.data.model.Signal
import com.reelwise.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `needs five ratings including a like, and ratings persist`() = runTest(main.dispatcher) {
        val repo = FakeRepository().apply { onboardingTitles = (1..10).map { title(it) } }
        val vm = OnboardingViewModel(repo)
        advanceUntilIdle()
        val titles = vm.state.value.titles
        titles.take(5).forEach { vm.rate(it, Signal.DISLIKE) }
        assertFalse(vm.state.value.canFinish)
        vm.rate(titles[5], Signal.LIKE)
        assertTrue(vm.state.value.canFinish)
        advanceUntilIdle()
        assertEquals(6, repo.interactionsFlow.value.size)

        vm.rate(titles[5], Signal.LIKE) // tapping again clears
        advanceUntilIdle()
        assertFalse(vm.state.value.canFinish)
        assertEquals(5, repo.interactionsFlow.value.size)
    }
}
