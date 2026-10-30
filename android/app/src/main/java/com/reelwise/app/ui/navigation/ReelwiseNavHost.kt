package com.reelwise.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.ask.AskRoute
import com.reelwise.app.ui.detail.DetailRoute
import com.reelwise.app.ui.home.BrowseRoute
import com.reelwise.app.ui.home.HomeRoute
import com.reelwise.app.ui.keys.KeysRoute
import com.reelwise.app.ui.onboarding.QuizRoute
import com.reelwise.app.ui.profile.ProfileRoute
import com.reelwise.app.ui.radial.LocalRadialMenu
import com.reelwise.app.ui.radial.RadialMenuHost
import com.reelwise.app.ui.radial.RadialMenuState
import com.reelwise.app.ui.theme.BrandRed
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
    Tab(Dest.Browse, { it.hasRoute<Dest.Browse>() }, "Browse", Icons.Rounded.ViewCarousel, Icons.Outlined.ViewCarousel),
    Tab(Dest.Ask, { it.hasRoute<Dest.Ask>() }, "Ask", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    Tab(Dest.Watchlist, { it.hasRoute<Dest.Watchlist>() }, "My list", Icons.Rounded.Bookmark, Icons.Outlined.BookmarkBorder),
    Tab(Dest.Profile, { it.hasRoute<Dest.Profile>() }, "Profile", Icons.Rounded.Person, Icons.Outlined.Person),
)

@Composable
fun ReelwiseNavHost(start: Start, navController: NavHostController = rememberNavController()) {
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
    val startFresh: (Any) -> Unit = { route ->
        navController.navigate(route) { popUpTo(navController.graph.id) { inclusive = true } }
    }

    val radial = remember { RadialMenuState() }
    val blur by animateDpAsState(if (radial.isOpen) 18.dp else 0.dp, tween(220), label = "blur")
    CompositionLocalProvider(LocalRadialMenu provides radial) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      Box(Modifier.fillMaxSize().blur(blur)) {
        NavHost(
            navController = navController,
            startDestination = when (start) {
                Start.KEYS -> Dest.Keys()
                Start.QUIZ -> Dest.Onboarding
                Start.HOME -> Dest.Home
            },
            enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<Dest.Keys> { entry ->
                val fromProfile = entry.toRoute<Dest.Keys>().fromProfile
                val back: (() -> Unit)? = if (fromProfile) { { navController.popBackStack() } } else null
                KeysRoute(
                    onDone = { if (fromProfile) navController.popBackStack() else startFresh(Dest.Onboarding) },
                    onBack = back,
                )
            }
            composable<Dest.Onboarding> { QuizRoute(onFinished = { startFresh(Dest.Home) }) }
            composable<Dest.Home> {
                HomeRoute(
                    onOpenTitle = openTitle,
                    onRateMore = { navController.navigate(Dest.Onboarding) },
                    onSearch = { goToTab(Dest.Ask) },
                )
            }
            composable<Dest.Browse> {
                BrowseRoute(
                    onOpenTitle = openTitle,
                    onRateMore = { navController.navigate(Dest.Onboarding) },
                    onSearch = { goToTab(Dest.Ask) },
                )
            }
            composable<Dest.Ask> { AskRoute(onOpenTitle = openTitle) }
            composable<Dest.Watchlist> { WatchlistRoute(onOpenTitle = openTitle, onBrowse = { goToTab(Dest.Home) }) }
            composable<Dest.Profile> {
                ProfileRoute(
                    onOpenTitle = openTitle,
                    onRetakeQuiz = { navController.navigate(Dest.Onboarding) },
                    onApiKeys = { navController.navigate(Dest.Keys(fromProfile = true)) },
                )
            }
            composable<Dest.Detail>(
                enterTransition = { slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(320)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(260)) },
            ) {
                DetailRoute(onBack = { navController.popBackStack() }, onOpenTitle = openTitle)
            }
        }

        AnimatedVisibility(
            visible = showBar,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FloatingNav(destination, goToTab)
        }
      }
        // Above everything, including the bottom bar; the app behind it is blurred.
        RadialMenuHost(radial)
    }
    }
}

/** A frosted, floating pill instead of a full-width bar, so posters show around it. */
@Composable
private fun FloatingNav(destination: NavDestination?, onTab: (Any) -> Unit) {
    val glass = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
    Surface(
        color = glass,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .shadow(16.dp, RoundedCornerShape(32.dp), clip = false),
    ) {
        Row(Modifier.fillMaxSize().selectableGroup(), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEach { tab ->
                val selected = destination?.hierarchy?.any(tab.matches) == true
                val tint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tab",
                )
                val dot by animateColorAsState(if (selected) BrandRed else Color.Transparent, label = "dot")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(28.dp))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTab(tab.route) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(if (selected) tab.selected else tab.unselected, contentDescription = null, tint = tint)
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(dot))
                }
            }
        }
    }
}

/** Space tab screens leave at the bottom so the floating bar never covers their last row. */
@Composable
fun navClearance(): Dp = 92.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
