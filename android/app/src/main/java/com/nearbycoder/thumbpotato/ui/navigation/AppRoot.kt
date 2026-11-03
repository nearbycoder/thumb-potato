package com.nearbycoder.thumbpotato.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nearbycoder.thumbpotato.AppContainer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.Mood
import com.nearbycoder.thumbpotato.domain.Platform
import com.nearbycoder.thumbpotato.ui.backlog.BacklogScreen
import com.nearbycoder.thumbpotato.ui.browse.BrowseScreen
import com.nearbycoder.thumbpotato.ui.browse.GridScreen
import com.nearbycoder.thumbpotato.ui.detail.DetailScreen
import com.nearbycoder.thumbpotato.ui.onboarding.OnboardingScreen
import com.nearbycoder.thumbpotato.ui.planner.PlannerScreen
import com.nearbycoder.thumbpotato.ui.settings.SettingsScreen
import com.nearbycoder.thumbpotato.ui.shuffle.ShuffleScreen
import com.nearbycoder.thumbpotato.ui.theme.Thumb
import com.nearbycoder.thumbpotato.ui.tonight.TonightScreen
import kotlin.reflect.KClass

private data class TopLevel(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val topLevels = listOf(
    TopLevel(TonightRoute, TonightRoute::class, "Tonight", Icons.Outlined.Home, Icons.Filled.Home),
    TopLevel(BrowseRoute, BrowseRoute::class, "Browse", Icons.Outlined.Explore, Icons.Filled.Explore),
    TopLevel(BacklogRoute, BacklogRoute::class, "Backlog", Icons.Outlined.Bookmarks, Icons.Filled.Bookmarks),
    TopLevel(PlannerRoute, PlannerRoute::class, "Planner", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    TopLevel(ShuffleRoute, ShuffleRoute::class, "Shuffle", Icons.Outlined.Shuffle, Icons.Filled.Shuffle),
)

/** Navigation actions shared by every screen. */
@Composable
fun rememberNavActions(navController: NavHostController): NavActions = remember(navController) { NavActions(navController) }

class NavActions(private val nav: NavHostController) {
    fun openGame(key: String) = nav.navigate(DetailRoute(key))
    fun openPlatform(platform: Platform) = nav.navigate(GridRoute("platform", platform.id))
    fun openMood(mood: Mood) = nav.navigate(GridRoute("mood", mood.id))
    fun openGenre(genre: Genre) = nav.navigate(GridRoute("genre", genre.name))
    fun openNewReleases() = nav.navigate(GridRoute("new"))
    fun openComingSoon() = nav.navigate(GridRoute("soon"))
    fun openSettings() = nav.navigate(SettingsRoute)
    fun back() { nav.popBackStack() }

    fun selectTab(route: Any) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppRoot(container: AppContainer, deepLink: Uri?, onDeepLinkConsumed: () -> Unit) {
    val navController = rememberNavController()
    val actions = rememberNavActions(navController)
    val events = remember { AppEvents() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val colors = Thumb.colors
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    // Rail on medium/expanded widths (tablets, unfolded foldables); bottom bar on compact phones.
    val suiteType = if (hasNavigationRail()) NavigationSuiteType.WideNavigationRailCollapsed else NavigationSuiteType.ShortNavigationBarCompact

    if (!settings.hasOnboarded) {
        CompositionLocalProvider(LocalAppContainer provides container) { OnboardingScreen() }
        return
    }

    LaunchedEffect(deepLink) {
        val uri = deepLink ?: return@LaunchedEffect
        when (uri.host) {
            "game" -> uri.pathSegments.firstOrNull()?.let { actions.openGame(it) }
            "platform" -> Platform.withId(uri.pathSegments.firstOrNull())?.let { actions.openPlatform(it) }
            "shuffle" -> actions.selectTab(ShuffleRoute)
            "planner" -> actions.selectTab(PlannerRoute)
            "backlog" -> actions.selectTab(BacklogRoute)
            "browse" -> actions.selectTab(BrowseRoute)
            else -> actions.selectTab(TonightRoute)
        }
        onDeepLinkConsumed()
    }

    val itemColors = ShortNavigationBarItemDefaults.colors(
        selectedIconColor = colors.accent,
        selectedTextColorTopIconPosition = colors.textPrimary,
        selectedTextColorStartIconPosition = colors.textPrimary,
        selectedIndicatorColor = colors.accent.copy(alpha = 0.16f),
        unselectedIconColor = colors.textSecondary,
        unselectedTextColor = colors.textSecondary,
    )

    CompositionLocalProvider(LocalAppContainer provides container, LocalAppEvents provides events) {
        NavigationSuiteScaffold(
            navigationItems = {
                topLevels.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
                    NavigationSuiteItem(
                        selected = selected,
                        onClick = { actions.selectTab(item.route) },
                        icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        navigationSuiteType = suiteType,
                        colors = itemColors,
                    )
                }
            },
            navigationSuiteType = suiteType,
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = colors.surface,
                navigationRailContainerColor = colors.surface,
                shortNavigationBarContainerColor = colors.surface,
                wideNavigationRailColors = WideNavigationRailDefaults.colors(containerColor = colors.surface),
            ),
            containerColor = colors.bg,
            contentColor = colors.textPrimary,
        ) {
            NavHost(
                navController = navController,
                startDestination = TonightRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 12 } },
            ) {
                composable<TonightRoute> { TonightScreen(actions) }
                composable<BrowseRoute> { BrowseScreen(actions) }
                composable<BacklogRoute> { BacklogScreen(actions) }
                composable<PlannerRoute> { PlannerScreen(actions) }
                composable<ShuffleRoute> { ShuffleScreen(actions) }
                composable<SettingsRoute> { SettingsScreen(actions) }
                composable<DetailRoute> { entry ->
                    val route = entry.toRoute<DetailRoute>()
                    DetailScreen(gameKey = route.key, actions = actions)
                }
                composable<GridRoute> { entry ->
                    val route = entry.toRoute<GridRoute>()
                    GridScreen(kind = route.kind, arg = route.arg, actions = actions)
                }
            }
        }
    }
}
