package com.reelwise.app

import com.reelwise.app.data.CachedFeed
import com.reelwise.app.data.model.FeedFilter
import com.reelwise.app.data.model.RecommendationResponse
import com.reelwise.app.data.model.Signal
import com.reelwise.app.ui.home.HomeViewModel
import com.reelwise.app.ui.home.buildSections
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeRepository().apply {
        nextFeed = RecommendationResponse((1..12).map { rec(it, because = if (it > 5) 100 else 101) }, "personalized")
    }

    private fun vm() = HomeViewModel(repo) { repo.now }

    @Test
    fun `first load fetches the feed and lays it out`() = runTest(main.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(listOf(FeedFilter.ALL), repo.refreshCalls)
        assertEquals(12, s.items.size)
        assertEquals("Title 100", s.sections.first().title)
        assertEquals("BECAUSE YOU LOVED", s.sections.first().eyebrow)
        assertFalse(s.firstLoad)
    }

    @Test
    fun `fresh cache is not refetched on open`() = runTest(main.dispatcher) {
        repo.feeds.getValue(FeedFilter.ALL).value = CachedFeed(repo.nextFeed, repo.now - 60_000)
        val vm = vm()
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()
        assertTrue(repo.refreshCalls.isEmpty())
        assertEquals(12, vm.state.value.items.size)
    }

    @Test
    fun `rating hides a title immediately and re-ranks after a pause`() = runTest(main.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()
        val first = vm.state.value.items.first().title
        vm.rate(first, Signal.DISMISS)
        advanceTimeBy(100)
        assertFalse(vm.state.value.items.any { it.title.key == first.key })
        assertEquals(1, repo.refreshCalls.size) // not yet: debounced
        advanceUntilIdle()
        assertEquals(2, repo.refreshCalls.size)
    }

    @Test
    fun `failed refresh keeps cached picks and reports the error`() = runTest(main.dispatcher) {
        repo.now = 10 * HomeViewModel.STALE_AFTER_MS
        repo.feeds.getValue(FeedFilter.ALL).value = CachedFeed(repo.nextFeed, 0) // stale, so a refresh is tried
        repo.failRefresh = true
        val vm = vm()
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.hasContent)
        assertTrue(s.error!!.contains("connection"))
    }

    @Test
    fun `switching filter loads that feed`() = runTest(main.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()
        vm.setFilter(FeedFilter.SERIES)
        advanceUntilIdle()
        assertEquals(listOf(FeedFilter.ALL, FeedFilter.SERIES), repo.refreshCalls)
        assertEquals(FeedFilter.SERIES, vm.state.value.filter)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `sections group by explanation and keep every item exactly once`() {
        val items = (1..20).map { rec(it, because = listOf(100, 101, 102, null)[it % 4], reason = "r") }
        val (hero, sections) = buildSections(items, currentYear = 2026)
        val all = hero + sections.flatMap { it.items }
        assertEquals(items.toSet(), all.toSet())
        assertEquals(items.size, all.size)
        assertEquals("More picks for you", sections.last().title)
    }

    @Test
    fun `fresh releases and series get their own rows, watched seeds say watched`() {
        val fresh = (1..4).map { rec(10 + it).let { r -> r.copy(title = r.title.copy(year = 2026)) } }
        val watched = (1..3).map { rec(20 + it, because = 7, reason = "Because you watched Title 7") }
        val series = (1..3).map { rec(30 + it).let { r -> r.copy(title = r.title.copy(mediaType = com.reelwise.app.data.model.MediaType.TV)) } }
        val hero = (1..5).map { rec(it) }
        val (_, sections) = buildSections(hero + fresh + watched + series, currentYear = 2026)
        assertEquals(listOf("Fresh releases for you", "Title 7", "Series you'd binge"), sections.map { it.title })
        assertEquals("BECAUSE YOU WATCHED", sections[1].eyebrow)
        assertEquals("NEW · 2025–26", sections[0].eyebrow)
    }
}
