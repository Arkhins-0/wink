package com.arkhins.wink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.SavedDocument
import com.arkhins.wink.ui.components.BottomNav
import com.arkhins.wink.ui.components.PopupCard
import com.arkhins.wink.ui.components.TopBar
import com.arkhins.wink.ui.components.UpdateAvailableDialog
import com.arkhins.wink.ui.screens.AccountScreen
import com.arkhins.wink.ui.screens.ChatScreen
import com.arkhins.wink.ui.screens.ChatsScreen
import com.arkhins.wink.ui.screens.ComposeScreen
import com.arkhins.wink.ui.screens.EmailScreen
import com.arkhins.wink.ui.screens.ForgotScreen
import com.arkhins.wink.ui.screens.HomeScreen
import com.arkhins.wink.ui.screens.LoginScreen
import com.arkhins.wink.ui.screens.NewPersonScreen
import com.arkhins.wink.ui.screens.OnboardingScreen
import com.arkhins.wink.ui.screens.PdfScreen
import com.arkhins.wink.ui.screens.PeopleScreen
import com.arkhins.wink.ui.screens.PermissionScreen
import com.arkhins.wink.ui.screens.PersonScreen
import com.arkhins.wink.ui.screens.ScannerScreen
import com.arkhins.wink.ui.screens.ScheduleScreen
import com.arkhins.wink.ui.screens.SetPasswordScreen
import com.arkhins.wink.ui.screens.WeekendScreen
import com.arkhins.wink.ui.screens.allGranted
import com.arkhins.wink.ui.theme.Night

/**
 * The whole app: the permission gate first, then sign-in or the signed-in
 * screens, with the update popup over whatever is showing.
 */
@Composable
fun WinkApp() {
    val app = LocalApp.current
    val context = LocalContext.current
    val vm = rememberViewModel { AppViewModel(app) }
    val uri = LocalUriHandler.current
    var granted by remember { mutableStateOf(allGranted(context)) }

    // Coming back from settings (permissions, or "allow installs"): pick up where we left off.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = allGranted(context)
                if (vm.updateStage is UpdateStage.NeedsPermission) vm.install()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        if (!granted) {
            PermissionScreen { granted = true }
        } else {
            when (vm.gate) {
                Gate.Loading -> Unit
                Gate.SignedOut -> AuthNav(vm)
                Gate.Onboarding -> OnboardingScreen { vm.refreshMe() }
                Gate.Ready -> MainNav(vm)
            }
        }

        val update = vm.updateInfo
        if (update != null && !vm.updateDismissed) {
            UpdateAvailableDialog(
                info = update,
                stage = vm.updateStage,
                onUpdate = vm::downloadAndInstall,
                onInstall = vm::install,
                onOpenSettings = vm::openInstallSettings,
                onOpenReleasePage = {
                    uri.openSafely(update.releaseUrl)
                    vm.dismissUpdate()
                },
                onDismiss = vm::dismissUpdate,
            )
        }
    }
}

/** Signed out: sign in, forgot password, and the invite/reset links. */
@Composable
private fun AuthNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val pending by Links.pending.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        val link = pending ?: return@LaunchedEffect
        val route = Links.route(link)
        if (route != null && route.startsWith("setpassword/")) {
            Links.pending.value = null
            nav.navigate(route)
        }
        // Any other link waits for sign-in; MainNav picks it up.
    }

    NavHost(nav, startDestination = "login") {
        composable("login") { LoginScreen(onSignedIn = vm::signedIn, onForgot = { nav.navigate("forgot") }) }
        composable("forgot") { ForgotScreen(onBack = { nav.popBackStack() }) }
        composable("setpassword/{kind}/{token}") { entry ->
            SetPasswordScreen(
                kind = entry.arguments?.getString("kind") ?: "invite",
                token = entry.arguments?.getString("token") ?: "",
                onSignedIn = vm::signedIn,
                onDone = { nav.navigate("login") { popUpTo("login") { inclusive = true } } },
            )
        }
    }
}

/** Signed in and set up: the five tabs and everything they open. */
@Composable
private fun MainNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val tab = route.substringBefore("/").substringBefore("?")
    var title by remember { mutableStateOf("") }
    var pdf by remember { mutableStateOf<SavedDocument?>(null) }
    val pending by Links.pending.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        val link = pending ?: return@LaunchedEffect
        Links.pending.value = null
        Links.route(link)?.let { r -> if (!r.startsWith("setpassword/")) nav.navigate(r) }
    }

    val openWeekend: (String) -> Unit = { nav.navigate("weekend/$it") }
    val openPdf: (SavedDocument) -> Unit = { pdf = it; nav.navigate("pdf") }
    val isTab = tab in setOf("home", "schedule", "chats", "people", "account")
    val screenTitle = when (tab) {
        "home" -> "Wink"
        "schedule" -> "Schedule"
        "chats" -> "Chats"
        "people" -> "People"
        "account" -> "Account"
        "compose" -> "New message"
        "newperson" -> "Add person"
        "email" -> "Email"
        "scanner", "verify" -> "Verify"
        "pdf" -> pdf?.name ?: "Document"
        "weekend" -> "Race weekend"
        else -> title
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = screenTitle,
            onBack = if (isTab) null else ({ nav.popBackStack() }),
            onOpenWeekend = openWeekend,
            showCountdown = tab != "pdf",
        )
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = "home") {
                composable("home") { HomeScreen(vm, highlight = null, onOpenWeekend = openWeekend, onCompose = { nav.navigate("compose") }, onOpenPdf = openPdf) }
                composable("home?m={m}") { e -> HomeScreen(vm, highlight = e.arguments?.getString("m"), onOpenWeekend = openWeekend, onCompose = { nav.navigate("compose") }, onOpenPdf = openPdf) }
                composable("schedule") { ScheduleScreen(isAdmin = vm.me?.isAdmin == true, onOpenWeekend = openWeekend) }
                composable("weekend/{id}") { e -> WeekendScreen(vm, e.arguments?.getString("id") ?: "", openPdf) }
                composable("chats") { ChatsScreen(vm) { nav.navigate("chat/$it") } }
                composable("chat/{id}") { e -> ChatScreen(vm, e.arguments?.getString("id") ?: "", openPdf) { title = it } }
                composable("compose") { ComposeScreen { nav.popBackStack(); vm.changed() } }
                composable("people") { PeopleScreen(vm.me, onOpen = { nav.navigate("person/$it") }, onAdd = { nav.navigate("newperson") }, onEmail = { g -> nav.navigate(if (g == null) "email" else "email?group=$g") }) }
                composable("person/{id}") { e -> PersonScreen(vm.me, e.arguments?.getString("id") ?: "", onOpenChat = { nav.navigate("chat/$it") }) { title = it } }
                composable("newperson") { NewPersonScreen(vm.me) { id -> nav.navigate("person/$id") { popUpTo("people") } } }
                composable("email") { EmailScreen(null) { nav.popBackStack() } }
                composable("email?group={group}") { e -> EmailScreen(e.arguments?.getString("group")) { nav.popBackStack() } }
                composable("account") { AccountScreen(vm) { nav.navigate("scanner") } }
                composable("scanner") { ScannerScreen() }
                composable("verify/{token}") { e -> ScannerScreen(initialToken = e.arguments?.getString("token")) }
                composable("pdf") { pdf?.let { PdfScreen(it) } }
            }
            vm.popup?.let { event ->
                Box(Modifier.align(Alignment.TopCenter)) {
                    PopupCard(event, onOpen = { link -> vm.dismissPopup(); Links.route(link)?.let { nav.navigate(it) } }, onDismiss = vm::dismissPopup)
                }
            }
        }
        if (isTab) {
            BottomNav(current = tab, unread = vm.unread) { dest ->
                nav.navigate(dest) {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
}

/** Open a link, or do nothing if no browser is installed. */
fun UriHandler.openSafely(url: String) {
    runCatching { openUri(url) }
}
