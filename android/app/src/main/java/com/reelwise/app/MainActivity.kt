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
import com.reelwise.app.ui.theme.ReelwiseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = repository.settings.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        // Hold the splash until settings load, so we never flash onboarding at a returning user.
        splash.setKeepOnScreenCondition { settings.value == null }

        setContent {
            val current: Settings? by settings.collectAsStateWithLifecycle()
            current?.let { s ->
                // Decided once: finishing onboarding later navigates instead of swapping the graph.
                val startOnboarding = remember { !s.onboardingDone }
                ReelwiseTheme(dynamicColor = s.dynamicColor) {
                    ReelwiseNavHost(startOnboarding = startOnboarding)
                }
            }
        }
    }
}
