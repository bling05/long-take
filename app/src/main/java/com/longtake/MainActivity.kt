package com.longtake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.longtake.data.ThemeMode
import com.longtake.social.VideoRef
import com.longtake.ui.BlockedChannelsScreen
import com.longtake.ui.DiscoverScreen
import com.longtake.ui.FriendsScreen
import com.longtake.ui.LibraryScreen
import com.longtake.ui.LocalIsDark
import com.longtake.ui.LongTakeTheme
import com.longtake.ui.ProfileScreen
import com.longtake.ui.SearchScreen
import com.longtake.ui.SettingsScreen
import com.longtake.ui.SourcesScreen
import com.longtake.ui.VideoScreen
import com.longtake.ui.YouScreen
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("discover", "Discover", Icons.Filled.Explore),
    Tab("search", "Search", Icons.Filled.Search),
    Tab("friends", "Friends", Icons.Filled.People),
    Tab("library", "Library", Icons.AutoMirrored.Filled.LibraryBooks),
    Tab("you", "You", Icons.Filled.AccountCircle),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LongTakeApp
        val repo = app.repo
        setContent {
            val prefs by repo.settings.flow.collectAsStateWithLifecycle(initialValue = null)
            LongTakeTheme(prefs?.theme ?: ThemeMode.DARK) {
                // Light status-bar icons on the dark theme, dark icons on the light one.
                val dark = LocalIsDark.current
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                val scope = rememberCoroutineScope()
                val openVideo: (String) -> Unit = { id -> nav.navigate("video/$id") }
                // Videos from other people's profiles may not be on this phone yet.
                val openRef: (VideoRef) -> Unit = { ref -> scope.launch { repo.ensureVideo(ref); nav.navigate("video/${ref.videoId}") } }
                val openProfile: (String) -> Unit = { uid -> nav.navigate("profile/$uid") }
                Scaffold(
                    bottomBar = {
                        if (route?.startsWith("video") != true) {
                            NavigationBar {
                                tabs.forEach { t ->
                                    NavigationBarItem(
                                        selected = route == t.route || (t.route == "you" && route in setOf("sources", "settings")),
                                        onClick = {
                                            nav.navigate(t.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(t.icon, null) },
                                        label = { Text(t.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { pad ->
                    NavHost(nav, startDestination = "discover", modifier = Modifier
                        .padding(bottom = pad.calculateBottomPadding())
                        // Only the bottom bar's space is taken here; each screen's top bar handles the status bar itself.
                        .consumeWindowInsets(PaddingValues(bottom = pad.calculateBottomPadding()))) {
                        composable("discover") { DiscoverScreen(repo, openVideo) }
                        composable("search") { SearchScreen(repo, openVideo) }
                        composable("friends") { FriendsScreen(repo, openRef, openProfile) }
                        composable("library") { LibraryScreen(repo, openVideo) }
                        composable("you") {
                            YouScreen(repo, openRef, onOpenSources = { nav.navigate("sources") }, onOpenSettings = { nav.navigate("settings") })
                        }
                        composable("sources") { SourcesScreen(repo) }
                        composable("settings") { SettingsScreen(repo, onOpenBlocked = { nav.navigate("blocked") }) }
                        composable("blocked") { BlockedChannelsScreen(repo, onBack = { nav.popBackStack() }) }
                        composable("video/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            VideoScreen(repo, e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onOpenProfile = openProfile)
                        }
                        composable("profile/{uid}", arguments = listOf(navArgument("uid") { type = NavType.StringType })) { e ->
                            ProfileScreen(repo, e.arguments?.getString("uid").orEmpty(), onBack = { nav.popBackStack() }, onOpenVideo = openRef, onOpenProfile = openProfile)
                        }
                    }
                }
            }
        }
    }
}
