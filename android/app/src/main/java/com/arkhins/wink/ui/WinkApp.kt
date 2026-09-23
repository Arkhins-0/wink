package com.arkhins.wink.ui

import com.arkhins.wink.ui.screens.LegalScreen
import com.arkhins.wink.ui.screens.ChangelogScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.arkhins.wink.data.FileInfo
import com.arkhins.wink.data.SavedDocument
import com.arkhins.wink.ui.components.FileView
import com.arkhins.wink.ui.components.BottomNav
import com.arkhins.wink.ui.components.PopupCard
import com.arkhins.wink.ui.components.TopBar
import com.arkhins.wink.ui.components.UpdateAvailableDialog
import com.arkhins.wink.ui.screens.AccountScreen
import com.arkhins.wink.ui.screens.ArchiveScreen
import com.arkhins.wink.ui.screens.SeasonArchiveScreen
import com.arkhins.wink.ui.screens.ChatScreen
import com.arkhins.wink.ui.screens.ChatsScreen
import com.arkhins.wink.ui.screens.ComposeScreen
import com.arkhins.wink.ui.screens.EmailScreen
import com.arkhins.wink.ui.screens.ForgotScreen
import com.arkhins.wink.ui.screens.HomeScreen
import com.arkhins.wink.ui.screens.ImageScreen
import com.arkhins.wink.ui.screens.LoginScreen
import com.arkhins.wink.ui.screens.NewChatScreen
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
import kotlinx.coroutines.launch

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

    // Phones that installed before the battery step existed get the dialog once.
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    LaunchedEffect(vm.gate, granted) {
        if (granted && vm.gate == Gate.Ready && !app.session.batteryAsked) {
            app.session.markBatteryAsked()
            if (!Battery.isExempt(context)) runCatching { batteryLauncher.launch(Battery.requestExemption(context)) }
        }
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
                onLegal = { nav.navigate("legal/$it") },
            )
        }
        composable("legal/{doc}") { e ->
            LegalScreen(e.arguments?.getString("doc") ?: "privacy", onOpen = { nav.navigate("legal/$it") }, onBack = { nav.popBackStack() })
        }
    }
}

/** Signed in and set up: the five tabs and everything they open. */
@Composable
private fun MainNav(vm: AppViewModel) {
    val app = LocalApp.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val tab = route.substringBefore("/").substringBefore("?")
    var title by remember { mutableStateOf("") }
    var pdf by remember { mutableStateOf<SavedDocument?>(null) }
    var image by remember { mutableStateOf<FileInfo?>(null) }
    val pending by Links.pending.collectAsStateWithLifecycle()
    // Opened by a notification or link: that decides the screen, not the last one seen.
    val openedByLink = remember { Links.pending.value != null }

    LaunchedEffect(pending) {
        val link = pending ?: return@LaunchedEffect
        Links.pending.value = null
        Links.route(link)?.let { r -> if (!r.startsWith("setpassword/")) nav.navigate(r) }
    }

    // The system may kill the app while the phone is locked and start it
    // again from scratch. The last screen is kept in DataStore, so a restart
    // within a few hours goes back to it (unless a link or notification says
    // where to go instead).
    val restoreScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val saved = app.session.lastRoute
        val fresh = System.currentTimeMillis() - app.session.lastRouteAt < 6 * 60 * 60 * 1000L
        if (!openedByLink && Links.pending.value == null && saved != null && saved != "home" && fresh) {
            runCatching { nav.navigate(saved) { launchSingleTop = true } }
        }
    }
    DisposableEffect(nav) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, arguments ->
            val pattern = destination.route ?: return@OnDestinationChangedListener
            // Screens that make sense to come back to; not viewers or forms.
            val concrete = when {
                pattern in setOf("home", "schedule", "chats", "people", "account") -> pattern
                pattern.startsWith("chat/") || pattern.startsWith("weekend/") || pattern.startsWith("person/") ->
                    pattern.replace("{id}", arguments?.getString("id") ?: return@OnDestinationChangedListener)
                else -> return@OnDestinationChangedListener
            }
            restoreScope.launch { app.session.saveRoute(concrete) }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }

    val openWeekend: (String) -> Unit = { nav.navigate("weekend/$it") }
    val view: (FileView) -> Unit = {
        when (it) {
            is FileView.Pdf -> { pdf = it.doc; nav.navigate("pdf") }
            is FileView.Image -> { image = it.file; nav.navigate("image") }
        }
    }
    val isTab = tab in setOf("home", "schedule", "chats", "people", "account")
    val screenTitle = when (tab) {
        "home" -> "Wink"
        "schedule" -> "Schedule"
        "chats" -> "Chats"
        "people" -> "People"
        "account" -> "Account"
        "compose" -> "New message"
        "newchat" -> "New chat"
        "newperson" -> "Add person"
        "email" -> "Email"
        "scanner", "verify" -> "Verify"
        "changelog" -> "What's new"
        "archive" -> if (route == "archive") "Archive" else title.ifBlank { "Season" }
        "pdf" -> pdf?.name ?: "Document"
        "image" -> image?.name ?: "Photo"
        "weekend" -> "Race weekend"
        else -> title
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = screenTitle,
            onBack = if (isTab) null else ({ nav.popBackStack() }),
            onOpenWeekend = openWeekend,
            showCountdown = tab != "pdf" && tab != "image",
        )
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = "home") {
                composable("home") { HomeScreen(vm, highlight = null, onOpenWeekend = openWeekend, onOpenChat = { nav.navigate("chat/$it") }, onAllChats = { nav.navigate("chats") { popUpTo("home"); launchSingleTop = true } }, onCompose = { nav.navigate("compose") }, onView = view) }
                composable("home?m={m}") { e -> HomeScreen(vm, highlight = e.arguments?.getString("m"), onOpenWeekend = openWeekend, onOpenChat = { nav.navigate("chat/$it") }, onAllChats = { nav.navigate("chats") { popUpTo("home"); launchSingleTop = true } }, onCompose = { nav.navigate("compose") }, onView = view) }
                composable("schedule") { ScheduleScreen(isAdmin = vm.me?.isAdmin == true, onOpenWeekend = openWeekend, onArchive = { nav.navigate("archive") }) }
                composable("weekend/{id}") { e -> WeekendScreen(vm, e.arguments?.getString("id") ?: "", view) }
                composable("chats") { ChatsScreen(vm, onOpen = { nav.navigate("chat/$it") }, onNewChat = { nav.navigate("newchat") }) }
                composable("newchat") { NewChatScreen { id -> nav.navigate("chat/$id") { popUpTo("chats") } } }
                composable("chat/{id}") { e -> ChatScreen(vm, e.arguments?.getString("id") ?: "", view) { title = it } }
                composable("compose") { ComposeScreen { nav.popBackStack(); vm.changed() } }
                composable("people") { PeopleScreen(vm.me, onOpen = { nav.navigate("person/$it") }, onAdd = { nav.navigate("newperson") }, onEmail = { g -> nav.navigate(if (g == null) "email" else "email?group=$g") }) }
                composable("person/{id}") { e -> PersonScreen(vm.me, e.arguments?.getString("id") ?: "", onOpenChat = { nav.navigate("chat/$it") }) { title = it } }
                composable("newperson") { NewPersonScreen(vm.me) { id -> nav.navigate("person/$id") { popUpTo("people") } } }
                composable("email") { EmailScreen(null) { nav.popBackStack() } }
                composable("email?group={group}") { e -> EmailScreen(e.arguments?.getString("group")) { nav.popBackStack() } }
                composable("account") { AccountScreen(vm, onScan = { nav.navigate("scanner") }, onArchive = { nav.navigate("archive") }, onChangelog = { nav.navigate("changelog") }, onLegal = { nav.navigate("legal/$it") }) }
                composable("changelog") { ChangelogScreen() }
                composable("legal/{doc}") { e -> LegalScreen(e.arguments?.getString("doc") ?: "privacy", onOpen = { nav.navigate("legal/$it") }, onTitle = { title = it }) }
                composable("archive") { ArchiveScreen { nav.navigate("archive/$it") } }
                composable("archive/{id}") { e -> SeasonArchiveScreen(vm, e.arguments?.getString("id") ?: "", onView = view, onDeleted = { nav.popBackStack() }) { title = it } }
                composable("scanner") { ScannerScreen(onOpenChat = { nav.navigate("chat/$it") }) }
                composable("verify/{token}") { e -> ScannerScreen(initialToken = e.arguments?.getString("token"), onOpenChat = { nav.navigate("chat/$it") }) }
                composable("pdf") { pdf?.let { PdfScreen(it) } }
                composable("image") { image?.let { ImageScreen(it) } }
            }
            vm.popup?.let { event ->
                Box(Modifier.align(Alignment.TopCenter)) {
                    PopupCard(event, onOpen = { link -> vm.dismissPopup(); Links.route(link)?.let { nav.navigate(it) } }, onDismiss = vm::dismissPopup)
                }
            }
        }
        if (isTab) {
            // A tab always shows its own page: everything above Home is
            // dropped first, nothing is restored (a chat opened from a popup
            // would otherwise come back on top of Home).
            BottomNav(
                current = tab,
                unreadHome = vm.unreadHome,
                unreadChats = vm.unreadChats,
                photoUrl = app.api.absolute(vm.me?.user?.photoUrl),
                name = vm.me?.user?.displayName ?: "?",
            ) { dest ->
                nav.navigate(dest) {
                    popUpTo("home") { inclusive = dest == "home" }
                    launchSingleTop = true
                }
            }
        }
    }
}

/** Open a link, or do nothing if no browser is installed. */
fun UriHandler.openSafely(url: String) {
    runCatching { openUri(url) }
}
