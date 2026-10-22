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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nube.physiohelper.App
import com.nube.physiohelper.reminders.Notifications

sealed interface Route {
    data object Home : Route
    data object Session : Route
    data object CheckIn : Route
    data object Spadi : Route
    data object History : Route
    data class SessionDetail(val id: Long) : Route
    data object Analysis : Route
    data object Program : Route
    data class EditExercise(val id: String?) : Route
    data object Settings : Route
    data object Battery : Route
    data object Data : Route
    data object Questions : Route
}

private fun routeFor(name: String?): Route? = when (name) {
    "home" -> Route.Home
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

@Composable
fun AppRoot(deepLink: String?, consumeDeepLink: () -> Unit) {
    val ctx = LocalContext.current
    var stack by remember { mutableStateOf(listOf<Route>(Route.Home)) }
    val push: (Route) -> Unit = { r -> stack = stack + r }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }

    LaunchedEffect(deepLink) {
        val r = routeFor(deepLink)
        if (r != null) stack = if (r == Route.Home) listOf(Route.Home) else listOf(Route.Home, r)
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

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val r = stack.last()) {
        Route.Home -> HomeScreen(push)
        Route.Session -> SessionScreen(pop)
        Route.CheckIn -> CheckInScreen(pop)
        Route.Spadi -> SpadiScreen(pop)
        Route.History -> HistoryScreen(push, pop)
        is Route.SessionDetail -> SessionDetailScreen(r.id, pop)
        Route.Analysis -> AnalysisScreen(pop)
        Route.Program -> ProgramScreen(push, pop)
        is Route.EditExercise -> EditExerciseScreen(r.id, pop)
        Route.Settings -> SettingsScreen(push, pop)
        Route.Battery -> BatteryScreen(pop)
        Route.Data -> DataScreen(pop)
        Route.Questions -> QuestionsScreen(pop)
    }
}
