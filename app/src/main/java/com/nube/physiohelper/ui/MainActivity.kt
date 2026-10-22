package com.nube.physiohelper.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nube.physiohelper.App
import com.nube.physiohelper.reminders.Notifications

sealed interface Route {
    // tabs
    data object Today : Route
    data object Progress : Route
    data object Program : Route
    data object More : Route

    // full-screen flows above the tabs
    data object Session : Route
    data object CheckIn : Route
    data object Spadi : Route
    data class SessionDetail(val id: Long) : Route
    data class EditExercise(val id: String?) : Route
    data object Settings : Route
    data object Battery : Route
    data object Data : Route
    data object Questions : Route
}

private data class TabItem(val route: Route, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Route.Today, "Today", Icons.Filled.Home),
    TabItem(Route.Progress, "Progress", Icons.Filled.DateRange),
    TabItem(Route.Program, "Program", Icons.Filled.Star),
    TabItem(Route.More, "More", Icons.Filled.Menu),
)

private fun Route.isTab() = tabs.any { it.route == this }

private fun routeFor(name: String?): Route? = when (name) {
    "home" -> Route.Today
    "session" -> Route.Session
    "checkin" -> Route.CheckIn
    "spadi" -> Route.Spadi
    "questions" -> Route.Questions
    "data" -> Route.Data
    "battery" -> Route.Battery
    else -> null
}

class MainActivity : ComponentActivity() {
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = intent?.getStringExtra(Notifications.EXTRA_ROUTE)
        setContent {
            PhysioTheme {
                AppRoot(deepLink.value) { deepLink.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = intent.getStringExtra(Notifications.EXTRA_ROUTE)
    }
}

/** The visible screen: either a tab, or the top of the overlay stack. */
private data class NavState(val tab: Route, val stack: List<Route>, val forward: Boolean) {
    val top: Route get() = stack.lastOrNull() ?: tab
}

@Composable
fun AppRoot(deepLink: String?, consumeDeepLink: () -> Unit) {
    val ctx = LocalContext.current
    var nav by remember { mutableStateOf(NavState(Route.Today, emptyList(), true)) }

    val go: (Route) -> Unit = { r ->
        nav = if (r.isTab()) NavState(r, emptyList(), true) else nav.copy(stack = nav.stack + r, forward = true)
    }
    val back: () -> Unit = {
        if (nav.stack.isNotEmpty()) nav = nav.copy(stack = nav.stack.dropLast(1), forward = false)
    }

    LaunchedEffect(deepLink) {
        val r = routeFor(deepLink)
        if (r != null) nav = if (r.isTab()) NavState(r, emptyList(), true) else NavState(Route.Today, listOf(r), true)
        if (deepLink != null) consumeDeepLink()
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val s = App.settings
        if (Build.VERSION.SDK_INT >= 33 && !s.notificationPermissionAsked &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            s.notificationPermissionAsked = true
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab != Route.Today) {
        if (nav.stack.isNotEmpty()) back() else nav = NavState(Route.Today, emptyList(), false)
    }

    val showBar = nav.stack.isEmpty()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = nav.tab == t.route,
                            onClick = { go(t.route) },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = if (showBar) pad.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f)),
        ) {
            AnimatedContent(
                targetState = nav,
                contentKey = { it.top },
                transitionSpec = {
                    val bothTabs = initialState.stack.isEmpty() && targetState.stack.isEmpty()
                    when {
                        bothTabs -> fadeIn(tween(220)) togetherWith fadeOut(tween(150))
                        targetState.forward -> (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(200)))
                        else -> (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)))
                    }
                },
                label = "nav",
            ) { state ->
                when (val r = state.top) {
                    Route.Today -> TodayScreen(go)
                    Route.Progress -> ProgressScreen(go)
                    Route.Program -> ProgramScreen(go)
                    Route.More -> MoreScreen(go)
                    Route.Session -> SessionScreen(back)
                    Route.CheckIn -> CheckInScreen(back)
                    Route.Spadi -> SpadiScreen(back)
                    is Route.SessionDetail -> SessionDetailScreen(r.id, back)
                    is Route.EditExercise -> EditExerciseScreen(r.id, back)
                    Route.Settings -> SettingsScreen(go, back)
                    Route.Battery -> BatteryScreen(back)
                    Route.Data -> DataScreen(back)
                    Route.Questions -> QuestionsScreen(back)
                }
            }
        }
    }
}
