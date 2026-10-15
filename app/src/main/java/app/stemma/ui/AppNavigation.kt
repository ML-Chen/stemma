package app.stemma.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.stemma.ui.entry.EntryScreen
import app.stemma.ui.explore.ExploreScreen
import app.stemma.ui.library.HistoryScreen
import app.stemma.ui.library.SavedScreen
import app.stemma.ui.search.SearchScreen
import app.stemma.ui.settings.AboutScreen
import app.stemma.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object ExploreRoute
@Serializable data object SavedRoute
@Serializable data object HistoryRoute
@Serializable data class SearchRoute(val query: String = "")
@Serializable data class EntryRoute(val title: String, val fragment: String? = null)
@Serializable data object SettingsRoute
@Serializable data object AboutRoute

/**
 * Something another app asked us to show (shared text, a selected word, a Wiktionary link).
 * With [replaceHome], the Explore screen underneath is removed, so Back returns to the other app.
 */
sealed interface ExternalRequest {
    val replaceHome: Boolean

    data class OpenEntry(val title: String, val fragment: String?, override val replaceHome: Boolean) : ExternalRequest
    data class Search(val query: String, override val replaceHome: Boolean) : ExternalRequest
}

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(ExploreRoute, ExploreRoute::class, "Explore", Icons.Outlined.Explore, Icons.Filled.Explore),
    Tab(SavedRoute, SavedRoute::class, "Saved", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    Tab(HistoryRoute, HistoryRoute::class, "History", Icons.Outlined.History, Icons.Filled.History),
)

@Composable
fun StemmaNavigation(requests: Flow<ExternalRequest>) {
    val nav = rememberNavController()

    LaunchedEffect(nav) {
        // The NavHost sets up its graph during layout, after this effect starts; wait for it.
        nav.currentBackStackEntryFlow.first()
        requests.collect { request ->
            val route: Any = when (request) {
                is ExternalRequest.OpenEntry -> EntryRoute(request.title, request.fragment)
                is ExternalRequest.Search -> SearchRoute(request.query)
            }
            // Only replace Explore when nothing else has been opened yet in this window.
            val onlyHome = nav.previousBackStackEntry == null &&
                nav.currentBackStackEntry?.destination?.hasRoute(ExploreRoute::class) == true
            nav.navigate(route) {
                if (request.replaceHome && onlyHome) popUpTo<ExploreRoute> { inclusive = true }
            }
        }
    }

    // Back arrows behave like the system Back button: go to the previous screen, or leave the
    // app when there is none (for example, an entry opened from another app's "Define" menu).
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val goBack: () -> Unit = { backDispatcher?.onBackPressed() }

    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = tabs.firstOrNull { tab -> destination?.hasRoute(tab.routeClass) == true }

    Scaffold(
        bottomBar = {
            if (currentTab != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    for (tab in tabs) {
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val openEntry: (String, String?) -> Unit = { title, fragment -> nav.navigate(EntryRoute(title, fragment)) }
        // Screens draw their own top bars; only the navigation bar's height is reserved here.
        val bottom = if (currentTab != null) padding.calculateBottomPadding() else 0.dp
        NavHost(
            navController = nav,
            startDestination = ExploreRoute,
            // Short transitions. The navigation library's default is a 700 ms cross-fade, which
            // makes every screen feel slow to open.
            enterTransition = { fadeIn(tween(ENTER_MS)) + slideInHorizontally(tween(ENTER_MS)) { it / 12 } },
            exitTransition = { fadeOut(tween(EXIT_MS)) },
            popEnterTransition = { fadeIn(tween(ENTER_MS)) },
            popExitTransition = { fadeOut(tween(EXIT_MS)) + slideOutHorizontally(tween(ENTER_MS)) { it / 12 } },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottom)
                .consumeWindowInsets(PaddingValues(bottom = bottom)),
        ) {
            composable<ExploreRoute> {
                ExploreScreen(
                    onSearch = { nav.navigate(SearchRoute()) },
                    onOpenEntry = openEntry,
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                    onOpenHistory = { nav.switchTab(HistoryRoute) },
                )
            }
            composable<SavedRoute> {
                SavedScreen(onOpenEntry = { openEntry(it, null) })
            }
            composable<HistoryRoute> {
                HistoryScreen(onOpenEntry = { openEntry(it, null) })
            }
            composable<SearchRoute> { entry ->
                val route = entry.toRoute<SearchRoute>()
                SearchScreen(
                    initialQuery = route.query,
                    onBack = goBack,
                    onOpenEntry = { title ->
                        // Replace the search screen, so Back from the entry returns to where search began.
                        nav.navigate(EntryRoute(title)) { popUpTo<SearchRoute> { inclusive = true } }
                    },
                )
            }
            composable<EntryRoute> { entry ->
                val route = entry.toRoute<EntryRoute>()
                EntryScreen(
                    title = route.title,
                    fragment = route.fragment,
                    onBack = goBack,
                    onOpenEntry = openEntry,
                    onSearch = { query -> nav.navigate(SearchRoute(query)) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onBack = goBack, onAbout = { nav.navigate(AboutRoute) })
            }
            composable<AboutRoute> {
                AboutScreen(onBack = goBack)
            }
        }
    }
}

private const val ENTER_MS = 180
private const val EXIT_MS = 120

private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
