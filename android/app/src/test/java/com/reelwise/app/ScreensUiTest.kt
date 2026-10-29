package com.reelwise.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.Providers
import com.reelwise.app.data.model.QueryIntent
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.model.TitleKey
import com.reelwise.app.data.model.TitleRef
import com.reelwise.app.ui.ask.AskContent
import com.reelwise.app.ui.ask.AskUiState
import com.reelwise.app.ui.ask.interpretationChips
import com.reelwise.app.ui.detail.DetailContent
import com.reelwise.app.ui.detail.DetailUiState
import com.reelwise.app.ui.home.HomeContent
import com.reelwise.app.ui.home.HomeUiState
import com.reelwise.app.ui.home.buildSections
import com.reelwise.app.ui.onboarding.OnboardingContent
import com.reelwise.app.ui.onboarding.OnboardingUiState
import com.reelwise.app.ui.theme.ReelwiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// A tall window so lazy lists compose every section under test.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class ScreensUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun onboardingUnlocksAfterFiveRatingsWithALike() {
        val titles = (1..6).map { title(it) }
        var state by mutableStateOf(OnboardingUiState(loading = false, titles = titles))
        compose.setContent {
            ReelwiseTheme {
                OnboardingContent(
                    state = state,
                    onRate = { t, s -> state = state.copy(ratings = state.ratings + (t.key to s)) },
                    onFinish = {},
                    onRetry = {},
                )
            }
        }
        compose.onNodeWithText("Like at least 1, rate 5").assertIsNotEnabled()
        listOf(1, 2, 3, 4).forEach { compose.onNodeWithContentDescription("Not for me: Title $it").performClick() }
        compose.onNodeWithContentDescription("Loved it: Title 5").performClick()
        compose.onNodeWithText("Show my picks").assertIsEnabled()
        assertEquals(Signal.LIKE, state.ratings[TitleKey(5, MediaType.MOVIE)])
    }

    @Test
    fun homeShowsExplanationsAndColdStartPrompt() {
        val (hero, sections) = buildSections((1..12).map { rec(it, because = 99) })
        var rateMore = 0
        compose.setContent {
            ReelwiseTheme {
                HomeContent(
                    state = HomeUiState(hero = hero, sections = sections, coldStart = true, firstLoad = false, hasContent = true),
                    onFilter = {}, onRefresh = {}, onOpenTitle = {}, onMore = {}, onRateMore = { rateMore++ },
                )
            }
        }
        compose.onNodeWithContentDescription("Top pick 1", substring = true).assertExists()
        // Every card here shares one reason, so it appears in several visible hero pages; the row heading is unique.
        compose.onNodeWithText("BECAUSE YOU LIKED").assertExists()
        compose.onAllNodesWithText("Because you liked Title 99", substring = true).onFirst().assertExists()
        compose.onNodeWithText("Rate titles").performClick()
        assertEquals(1, rateMore)
    }

    @Test
    fun homeErrorWithoutCacheOffersRetry() {
        var retries = 0
        compose.setContent {
            ReelwiseTheme {
                HomeContent(
                    state = HomeUiState(error = "Can't reach Reelwise.", firstLoad = false),
                    onFilter = {}, onRefresh = { retries++ }, onOpenTitle = {}, onMore = {}, onRateMore = {},
                )
            }
        }
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun askShowsHowTheRequestWasRead() {
        val response = AskResponse(
            interpretation = QueryIntent(mediaType = "tv", genres = listOf("science fiction"), excludeGenres = listOf("horror"),
                summary = "Sci-fi series without horror"),
            references = listOf(TitleRef(1, MediaType.TV, "Dark")),
            items = listOf(rec(3, because = 1, reason = "Fans of Dark also loved this")),
            parser = "llm",
        )
        compose.setContent {
            ReelwiseTheme {
                AskContent(AskUiState(query = "like Dark", result = response), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("HOW I READ THAT").assertExists()
        compose.onNodeWithText("No Horror").assertExists()
        compose.onNodeWithText("Like Dark").assertExists()
        compose.onNodeWithContentDescription("Fans of Dark also loved this", substring = true).assertExists()
    }

    @Test
    fun interpretationChipsCoverEveryFilter() {
        val chips = interpretationChips(
            QueryIntent("movie", listOf("comedy"), listOf("horror"), listOf("heist"), emptyList(), 1990, 1999, 7.0, "ko"),
            listOf("Parasite"),
        )
        assertEquals(listOf("Movies", "Comedy", "No Horror", "Like Parasite", "heist", "1990–1999", "Rated 7.0+", "Language: KO"), chips)
    }

    @Test
    fun detailSaysWhenNothingStreamsInRegion() {
        val detail = TitleDetail(summary = title(4), providers = Providers(), runtime = 125)
        var signal: Signal? = null
        compose.setContent {
            ReelwiseTheme {
                DetailContent(
                    state = DetailUiState(loading = false, detail = detail, region = "GB", signal = signal),
                    onBack = {}, onSignal = { signal = it }, onToggleWatchlist = {}, onOpenTitle = {}, onRetry = {}, onOpenUrl = {},
                )
            }
        }
        compose.onNodeWithText("Not available to stream, rent or buy in GB right now.").assertExists()
        compose.onNodeWithText("Loved it").performClick()
        assertEquals(Signal.LIKE, signal)
    }
}
