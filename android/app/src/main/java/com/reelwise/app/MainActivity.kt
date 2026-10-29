package com.reelwise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.reelwise.app.data.Repository
import com.reelwise.app.data.local.Settings
import com.reelwise.app.ui.navigation.ReelwiseNavHost
import com.reelwise.app.ui.navigation.Start
import com.reelwise.app.ui.theme.ReelwiseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val boot = combine(repository.settings, repository.hasTmdbKey) { s, hasKey -> s to hasKey }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        // Hold the splash until settings load, so a returning user never sees setup flash by.
        splash.setKeepOnScreenCondition { boot.value == null }

        setContent {
            val current: Pair<Settings, Boolean>? by boot.collectAsStateWithLifecycle()
            current?.let { (settings, hasKey) ->
                // Decided once: finishing setup later navigates instead of swapping the graph.
                val start = remember {
                    when {
                        !hasKey -> Start.KEYS
                        !settings.onboardingDone -> Start.QUIZ
                        else -> Start.HOME
                    }
                }
                ReelwiseTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                    ReelwiseNavHost(start = start)
                }
            }
        }
    }
}
