package com.reelwise.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.ask.AskRoute
import com.reelwise.app.ui.detail.DetailRoute
import com.reelwise.app.ui.home.HomeRoute
import com.reelwise.app.ui.onboarding.OnboardingRoute
import com.reelwise.app.ui.profile.ProfileRoute
import com.reelwise.app.ui.watchlist.WatchlistRoute

private class Tab(
    val route: Any,
    val matches: (NavDestination) -> Boolean,
    val label: String,
    val selected: ImageVector,
    val unselected: ImageVector,
)

private val tabs = listOf(
    Tab(Dest.Home, { it.hasRoute<Dest.Home>() }, "For you", Icons.Rounded.Home, Icons.Outlined.Home),
    Tab(Dest.Ask, { it.hasRoute<Dest.Ask>() }, "Ask", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    Tab(Dest.Watchlist, { it.hasRoute<Dest.Watchlist>() }, "Watchlist", Icons.Rounded.Bookmark, Icons.Outlined.BookmarkBorder),
    Tab(Dest.Profile, { it.hasRoute<Dest.Profile>() }, "Profile", Icons.Rounded.Person, Icons.Outlined.Person),
)

@Composable
fun ReelwiseNavHost(startOnboarding: Boolean, navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = tabs.any { tab -> destination?.hierarchy?.any(tab.matches) == true }

    val openTitle: (TitleSummary) -> Unit = { navController.navigate(Dest.Detail(it.mediaType.wire, it.id)) }
    val goToTab: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any(tab.matches) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { goToTab(tab.route) },
                            icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            NavHost(
                navController = navController,
                startDestination = if (startOnboarding) Dest.Onboarding else Dest.Home,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable<Dest.Onboarding> {
                    OnboardingRoute(onFinished = {
                        navController.navigate(Dest.Home) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    })
                }
                composable<Dest.Home> {
                    HomeRoute(onOpenTitle = openTitle, onRateMore = { navController.navigate(Dest.Onboarding) })
                }
                composable<Dest.Ask> { AskRoute(onOpenTitle = openTitle) }
                composable<Dest.Watchlist> { WatchlistRoute(onOpenTitle = openTitle, onBrowse = { goToTab(Dest.Home) }) }
                composable<Dest.Profile> {
                    ProfileRoute(onOpenTitle = openTitle, onRetakeQuiz = { navController.navigate(Dest.Onboarding) })
                }
                composable<Dest.Detail> {
                    DetailRoute(onBack = { navController.popBackStack() }, onOpenTitle = openTitle)
                }
            }
        }
    }
}
