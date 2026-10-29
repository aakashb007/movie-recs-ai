package com.reelwise.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.model.CastMember
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.Provider
import com.reelwise.app.data.model.Providers
import com.reelwise.app.data.model.QueryIntent
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.model.TitleRef
import com.reelwise.app.ui.ask.AskContent
import com.reelwise.app.ui.ask.AskUiState
import com.reelwise.app.ui.detail.DetailContent
import com.reelwise.app.ui.detail.DetailUiState
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders key screens to PNGs (build/outputs/roborazzi) so UI changes can be reviewed as images.
 * Artwork shows its title fallback here because tests have no network.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shoot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent { ReelwiseTheme(darkTheme = dark, content = content) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val feed = (1..14).map { i ->
        val r = rec(i, because = if (i % 2 == 0) 90 else 91, reason = "Because you liked Title ${if (i % 2 == 0) 90 else 91}")
        if (i in 6..8) r.copy(title = r.title.copy(year = 2026)) else r
    }

    @Test
    fun homeDark() = shoot("home_dark") { home() }

    @Test
    fun homeLight() = shoot("home_light", dark = false) { home() }

    @Composable
    private fun home() {
        val (hero, sections) = buildSections(feed, currentYear = 2026)
        HomeContent(HomeUiState(hero = hero, sections = sections, firstLoad = false, hasContent = true), HomeActions())
    }

    @Test
    fun homeLoading() = shoot("home_loading") {
        HomeContent(HomeUiState(), HomeActions())
    }

    private val watched = (1..9).map { title(it, genre = listOf("Drama", "Comedy", "Sci-Fi")[it % 3]) }

    @Test
    fun quizWatched() = shoot("quiz_1_watched") {
        QuizContent(
            QuizUiState(
                loadingPool = false, pool = watched,
                watched = watched.take(4).associateBy { it.key }, loved = setOf(watched[1].key),
            ),
            QuizActions(),
        )
    }

    @Test
    fun quizGenres() = shoot("quiz_2_genres") {
        QuizContent(
            QuizUiState(
                step = QuizStep.GENRES, loadingPool = false,
                genres = mapOf("science fiction" to GenreChoice.LOVE, "thriller" to GenreChoice.LOVE, "horror" to GenreChoice.AVOID),
            ),
            QuizActions(),
        )
    }

    @Test
    fun quizPrefs() = shoot("quiz_3_prefs") {
        QuizContent(QuizUiState(step = QuizStep.PREFS, loadingPool = false, languages = setOf("en", "hi")), QuizActions())
    }

    @Test
    fun quizReview() = shoot("quiz_4_review", dark = false) {
        QuizContent(
            QuizUiState(
                step = QuizStep.REVIEW, loadingPool = false, watched = watched.take(5).associateBy { it.key },
                loved = setOf(watched[0].key), genres = mapOf("crime" to GenreChoice.LOVE), languages = setOf("hi"),
            ),
            QuizActions(),
        )
    }

    @Test
    fun keys() = shoot("keys") {
        KeysContent(KeysUiState(tmdbKey = "abcdef123456"), {}, {}, {}, null, {})
    }

    @Test
    fun ask() = shoot("ask") {
        AskContent(
            AskUiState(
                query = "Something like Dark but lighter",
                result = AskResponse(
                    QueryIntent(mediaType = "tv", genres = listOf("science fiction", "mystery"), summary = "Twisty sci-fi mystery series, less bleak than Dark"),
                    listOf(TitleRef(1, MediaType.TV, "Dark")),
                    (2..6).map { rec(it, because = 1, reason = "Sci-Fi & Fantasy · like Dark") },
                    "llm",
                ),
            ),
            {}, {}, {}, {},
        )
    }

    @Test
    fun detail() = shoot("detail") {
        DetailContent(
            DetailUiState(
                loading = false,
                region = "GB",
                signal = Signal.LIKE,
                inWatchlist = true,
                detail = TitleDetail(
                    summary = title(4, genre = "Science Fiction").copy(title = "A Very Good Film", overview = "A long overview. ".repeat(8)),
                    tagline = "Some journeys change everything.",
                    runtime = 148,
                    trailerYoutubeKey = "abc",
                    cast = listOf(CastMember("Ada Actor", "Lead"), CastMember("Ben Bitpart", "Friend")),
                    providers = Providers(stream = listOf(Provider("StreamCo")), rent = listOf(Provider("RentIt"), Provider("Shop"))),
                    similar = (10..14).map { title(it) },
                ),
            ),
            {}, {}, {}, {}, {}, {},
        )
    }
}
