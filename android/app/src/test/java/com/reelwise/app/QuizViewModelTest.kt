package com.reelwise.app

import com.reelwise.app.data.model.Freshness
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.Signal
import com.reelwise.app.ui.onboarding.QuizStep
import com.reelwise.app.ui.onboarding.QuizViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `the whole quiz saves watched, loved and preferences together`() = runTest(main.dispatcher) {
        val repo = FakeRepository().apply { onboardingTitles = (1..12).map { title(it) } }
        val vm = QuizViewModel(repo)
        advanceUntilIdle()
        val titles = vm.state.value.pool

        titles.take(2).forEach(vm::toggleWatched)
        assertFalse("needs 3 watched", vm.state.value.canNext)
        vm.toggleWatched(titles[2])
        vm.toggleLoved(titles[2])
        vm.toggleLoved(titles[5]) // not watched: ignored
        assertEquals(setOf(titles[2].key), vm.state.value.loved)
        vm.next()
        assertEquals(QuizStep.GENRES, vm.state.value.step)

        assertFalse("needs a loved genre", vm.state.value.canNext)
        vm.cycleGenre("comedy") // love
        vm.cycleGenre("horror"); vm.cycleGenre("horror") // avoid
        vm.next()
        vm.setFreshness(Freshness.LATEST)
        vm.toggleLanguage("hi")
        vm.next()
        assertEquals(QuizStep.REVIEW, vm.state.value.step)
        vm.next()
        advanceUntilIdle()

        assertTrue(vm.state.value.done)
        val taste = repo.settingsFlow.value.taste
        assertEquals(listOf("comedy"), taste.lovedGenres)
        assertEquals(listOf("horror"), taste.avoidedGenres)
        assertEquals(Freshness.LATEST, taste.freshness)
        assertEquals(listOf("hi"), taste.languages)
        assertTrue(repo.settingsFlow.value.onboardingDone)
        val signals = repo.interactionsFlow.value.associate { it.id to it.signal }
        assertEquals(mapOf(1 to Signal.SEEN, 2 to Signal.SEEN, 3 to Signal.LIKE), signals)
    }

    @Test
    fun `unwatching also unloves`() = runTest(main.dispatcher) {
        val repo = FakeRepository().apply { onboardingTitles = (1..4).map { title(it) } }
        val vm = QuizViewModel(repo)
        advanceUntilIdle()
        val t = vm.state.value.pool.first()
        vm.toggleWatched(t)
        vm.toggleLoved(t)
        vm.toggleWatched(t)
        assertTrue(vm.state.value.watched.isEmpty())
        assertTrue(vm.state.value.loved.isEmpty())
    }

    @Test
    fun `search waits for the typing to pause, and kind filters results`() = runTest(main.dispatcher) {
        val repo = FakeRepository().apply { searchResults = listOf(title(50), title(51, MediaType.TV)) }
        val vm = QuizViewModel(repo)
        advanceUntilIdle()
        vm.onQuery("da")
        vm.onQuery("dar")
        vm.onQuery("dark")
        advanceTimeBy(QuizViewModel.SEARCH_DEBOUNCE_MS + 50)
        advanceUntilIdle()
        assertEquals(listOf("dark"), repo.searches)
        assertEquals(2, vm.state.value.visible.size)
        vm.setKind(com.reelwise.app.data.model.FeedFilter.SERIES)
        assertEquals(listOf(51), vm.state.value.visible.map { it.id })
    }

    @Test
    fun `retaking the quiz starts from what is already known`() = runTest(main.dispatcher) {
        val repo = FakeRepository()
        repo.rate(title(7), Signal.LIKE)
        repo.rate(title(8), Signal.SEEN)
        repo.rate(title(9), Signal.DISMISS)
        repo.settingsFlow.value = repo.settingsFlow.value.copy(
            taste = com.reelwise.app.data.model.TasteProfile(lovedGenres = listOf("crime"), freshness = Freshness.ANY),
        )
        val vm = QuizViewModel(repo)
        advanceUntilIdle()
        assertEquals(setOf(7, 8), vm.state.value.watched.keys.map { it.id }.toSet())
        assertEquals(setOf(7), vm.state.value.loved.map { it.id }.toSet())
        assertEquals(listOf("crime"), vm.state.value.lovedGenres)
        assertEquals(Freshness.ANY, vm.state.value.freshness)
    }
}
