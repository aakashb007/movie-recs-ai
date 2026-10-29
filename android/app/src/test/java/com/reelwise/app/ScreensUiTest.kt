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
import com.reelwise.app.data.model.Freshness
import com.reelwise.app.ui.home.HomeActions
import com.reelwise.app.ui.home.HomeContent
import com.reelwise.app.ui.home.HomeUiState
import com.reelwise.app.ui.home.buildSections
import com.reelwise.app.ui.keys.KeysContent
import com.reelwise.app.ui.keys.KeysUiState
import com.reelwise.app.ui.onboarding.GenreChoice
import com.reelwise.app.ui.onboarding.QuizActions
import com.reelwise.app.ui.onboarding.QuizContent
import com.reelwise.app.ui.onboarding.QuizStep
import com.reelwise.app.ui.onboarding.QuizUiState
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
    fun quizNeedsThreeWatchedTitlesBeforeNext() {
        var state by mutableStateOf(QuizUiState(loadingPool = false, pool = (1..6).map { title(it) }))
        compose.setContent {
            ReelwiseTheme {
                QuizContent(
                    state,
                    QuizActions(
                        onToggleWatched = { t ->
                            state = state.copy(watched = if (t.key in state.watched) state.watched - t.key else state.watched + (t.key to t))
                        },
                        onToggleLoved = { t -> state = state.copy(loved = state.loved + t.key) },
                    ),
                )
            }
        }
        compose.onNodeWithText("Next").assertIsNotEnabled()
        listOf(1, 2, 3).forEach { compose.onNodeWithContentDescription("Title $it, ${2000 + it}").performClick() }
        compose.onNodeWithText("Next").assertIsEnabled()
        compose.onNodeWithText("3 watched · 0 loved").assertExists()
        compose.onNodeWithContentDescription("Loved Title 2").performClick()
        assertEquals(setOf(TitleKey(2, MediaType.MOVIE)), state.loved)
    }

    @Test
    fun quizReviewShowsEveryAnswerWithEditLinks() {
        val watched = (1..3).associate { title(it).key to title(it) }
        var editedTo: QuizStep? = null
        compose.setContent {
            ReelwiseTheme {
                QuizContent(
                    QuizUiState(
                        step = QuizStep.REVIEW, loadingPool = false, watched = watched, loved = setOf(title(2).key),
                        genres = mapOf("comedy" to GenreChoice.LOVE, "horror" to GenreChoice.AVOID),
                        freshness = Freshness.LATEST, languages = setOf("hi"),
                    ),
                    QuizActions(onGoTo = { editedTo = it }),
                )
            }
        }
        compose.onNodeWithText("Comedy").assertExists()
        compose.onNodeWithText("Horror").assertExists()
        compose.onNodeWithText("Latest releases · Movies and series · Hindi").assertExists()
        compose.onNodeWithContentDescription("Edit genres you love").performClick()
        assertEquals(QuizStep.GENRES, editedTo)
        compose.onNodeWithText("Looks good, save").assertIsEnabled()
    }

    @Test
    fun keysScreenNeedsATmdbKeyAndShowsRejection() {
        var state by mutableStateOf(KeysUiState())
        compose.setContent {
            ReelwiseTheme { KeysContent(state, { state = state.copy(tmdbKey = it) }, {}, {}, null, {}) }
        }
        compose.onNodeWithText("Test & save").assertIsNotEnabled()
        state = state.copy(tmdbKey = "abc")
        compose.onNodeWithText("Test & save").assertIsEnabled()
        state = state.copy(error = "Your TMDB key was rejected. Check it in Settings.")
        compose.onNodeWithText("Your TMDB key was rejected. Check it in Settings.").assertExists()
    }

    @Test
    fun homeShowsExplanationsAndColdStartPrompt() {
        val (hero, sections) = buildSections((1..12).map { rec(it, because = 99) }, currentYear = 2026)
        var rateMore = 0
        compose.setContent {
            ReelwiseTheme {
                HomeContent(
                    state = HomeUiState(hero = hero, sections = sections, coldStart = true, firstLoad = false, hasContent = true),
                    actions = HomeActions(onRateMore = { rateMore++ }),
                )
            }
        }
        compose.onNodeWithContentDescription("Title 1, 2001", substring = true).assertExists()
        // Every card here shares one reason, so it appears in several visible hero pages; the row heading is unique.
        compose.onNodeWithText("BECAUSE YOU LOVED").assertExists()
        compose.onAllNodesWithText("Because you liked Title 99", substring = true).onFirst().assertExists()
        compose.onNodeWithText("Take quiz").performClick()
        assertEquals(1, rateMore)
    }

    @Test
    fun homeErrorWithoutCacheOffersRetry() {
        var retries = 0
        compose.setContent {
            ReelwiseTheme {
                HomeContent(
                    state = HomeUiState(error = "Add your TMDB key in Settings", firstLoad = false),
                    actions = HomeActions(onRefresh = { retries++ }),
                )
            }
        }
        compose.onNodeWithText("Add your TMDB key in Settings").assertExists()
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
