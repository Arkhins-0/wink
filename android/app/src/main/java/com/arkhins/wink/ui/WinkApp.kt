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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.FileInfo
import com.arkhins.wink.data.OtherUser
import com.arkhins.wink.data.SavedDocument
import com.arkhins.wink.ui.components.FileView
import com.arkhins.wink.ui.components.Avatar
import com.arkhins.wink.ui.components.BottomNav
import com.arkhins.wink.ui.components.PopupBubble
import com.arkhins.wink.ui.components.SelectionBar
import com.arkhins.wink.ui.components.SelectionTopBar
import com.arkhins.wink.ui.components.TopBar
import com.arkhins.wink.ui.components.ChatsHeader
import com.arkhins.wink.ui.components.UpdateAvailableDialog
import com.arkhins.wink.ui.components.WhatsNewDialog
import com.arkhins.wink.ui.screens.AccountScreen
import com.arkhins.wink.ui.screens.ArchiveScreen
import com.arkhins.wink.ui.screens.SeasonArchiveScreen
import com.arkhins.wink.ui.screens.ChatScreen
import com.arkhins.wink.ui.screens.ChatProfileScreen
import com.arkhins.wink.ui.screens.GroupScreen
import com.arkhins.wink.ui.screens.SettingsScreen
import com.arkhins.wink.ui.screens.AboutScreen
import com.arkhins.wink.ui.screens.AccountDetailsScreen
import com.arkhins.wink.ui.screens.StorageScreen
import com.arkhins.wink.ui.screens.NewGroupScreen
import com.arkhins.wink.ui.screens.ChatsScreen
import com.arkhins.wink.ui.screens.ComposeScreen
import com.arkhins.wink.ui.screens.EmailScreen
import com.arkhins.wink.ui.screens.ForgotScreen
import com.arkhins.wink.ui.screens.HomeScreen
import com.arkhins.wink.ui.screens.ImageScreen
import com.arkhins.wink.ui.screens.GalleryScreen
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

        // The first open after an update shows what changed; a newer release, if any, waits until that is closed.
        val whatsNew = vm.whatsNew
        if (whatsNew != null) {
            WhatsNewDialog(info = whatsNew, onDismiss = vm::dismissWhatsNew)
        }
        val update = vm.updateInfo
        if (whatsNew == null && update != null && !vm.updateDismissed) {
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

    NavHost(nav, startDestination = "login", enterTransition = { fadeIn(tween(120)) }, exitTransition = { fadeOut(tween(90)) }) {
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
    var chatWith by remember { mutableStateOf<OtherUser?>(null) }
    // Messages long-pressed in a chat: the header turns into the selection bar.
    var selection by remember { mutableStateOf<SelectionBar?>(null) }
    // The chat header's ⋮ menu: a search bar in the chat, or an export of it.
    var chatSearch by remember { mutableStateOf(false) }
    // The chats tab: 0 is the chat list, 1 the channels; the header switch and the swipe both move it.
    var chatsPage by remember { mutableIntStateOf(0) }
    var chatExport by remember { mutableIntStateOf(0) }
    var chatCanExport by remember { mutableStateOf(true) }
    // The open chat. It is not a screen of its own: it floats in over whatever is on screen and floats
    // back out, so the page beneath (the chats tab, its footer, the list) is never touched.
    var openChat by remember { mutableStateOf<String?>(null) }
    // The chat still drawn while it slides out.
    var lastChat by remember { mutableStateOf<String?>(null) }
    // A chat opens without the last one's search bar, selection or name in its header.
    LaunchedEffect(openChat) {
        openChat?.let { lastChat = it }
        chatSearch = false
        chatWith = null
        selection = null
    }
    /** Go where a link or a saved route says: a chat floats in, anything else is a screen. */
    val go: (String) -> Unit = { r -> if (r.startsWith("chat/")) openChat = r.removePrefix("chat/") else nav.navigate(r) }
    var pdf by remember { mutableStateOf<SavedDocument?>(null) }
    var image by remember { mutableStateOf<FileInfo?>(null) }
    // A grid's photos, opened from it; while some are picked there the header is the selection bar.
    var gallery by remember { mutableStateOf<FileView.Gallery?>(null) }
    var gallerySelection by remember { mutableStateOf<SelectionBar?>(null) }
    val pending by Links.pending.collectAsStateWithLifecycle()
    // Opened by a notification or link: that decides the screen, not the last one seen.
    val openedByLink = remember { Links.pending.value != null }

    LaunchedEffect(pending) {
        val link = pending ?: return@LaunchedEffect
        Links.pending.value = null
        Links.route(link)?.let { r -> if (!r.startsWith("setpassword/")) go(r) }
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
            if (saved.startsWith("chat/")) openChat = saved.removePrefix("chat/") else runCatching { nav.navigate(saved) { launchSingleTop = true } }
        }
    }
    LaunchedEffect(openChat) { openChat?.let { app.session.saveRoute("chat/$it") } }
    DisposableEffect(nav) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, arguments ->
            val pattern = destination.route ?: return@OnDestinationChangedListener
            // Screens that make sense to come back to; not viewers or forms.
            val concrete = when {
                pattern in setOf("home", "schedule", "chats", "people", "account") -> pattern
                pattern.startsWith("weekend/") || pattern.startsWith("person/") ->
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
            is FileView.Gallery -> { gallery = it; gallerySelection = null; nav.navigate("gallery") }
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
        "newgroup" -> "New group"
        "newperson" -> "Add person"
        "email" -> "Email"
        "scanner", "verify" -> "Verify"
        "changelog" -> "What's new"
        "settings" -> "Settings"
        "details" -> "Account"
        "storage" -> "Storage"
        "about" -> "About"
        "archive" -> if (route == "archive") "Archive" else title.ifBlank { "Season" }
        "pdf" -> pdf?.name ?: "Document"
        "image" -> image?.name ?: "Photo"
        // Who sent the photos and when, as WhatsApp heads them: "You · 8:16 pm".
        "gallery" -> gallery?.message?.let { m -> "${if (m.mine) "You" else m.sender?.name ?: "Wink"} · ${localTime(m.createdAt)}" } ?: "Photos"
        "weekend" -> "Race weekend"
        else -> title
    }
    // Screens the chat opens on top of itself; while one is up the chat waits underneath, out of sight.
    val chatCovered = tab in setOf("image", "pdf", "gallery", "chatprofile", "group")

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        val galleryBar = gallerySelection
        if (tab == "gallery" && galleryBar != null) SelectionTopBar(galleryBar) else TopBar(
            title = screenTitle,
            onBack = if (isTab) null else ({ nav.popBackStack() }),
            onOpenWeekend = openWeekend,
            showCountdown = tab != "pdf" && tab != "image" && tab != "gallery",
            center = if (tab == "chats") ({ ChatsHeader(chatsPage) { chatsPage = it } }) else null,
        )
        Box(Modifier.weight(1f)) {
            // Screens change in a blink: the default fade is far too slow.
            NavHost(
                nav,
                startDestination = "home",
                enterTransition = { fadeIn(tween(120)) },
                // A page flying in leaves the screen under it exactly as it was, and finds it there on the way back.
                exitTransition = { if (targetState.destination.route in slidingPages) ExitTransition.KeepUntilTransitionsFinished else fadeOut(tween(90)) },
                popEnterTransition = { if (initialState.destination.route in slidingPages) EnterTransition.None else fadeIn(tween(120)) },
                popExitTransition = { fadeOut(tween(90)) },
            ) {
                composable("home") { HomeScreen(vm, highlight = null, onOpenWeekend = openWeekend, onOpenChat = { openChat = it }, onAllChats = { nav.navigate("chats") { popUpTo("home"); launchSingleTop = true } }, onCompose = { nav.navigate("compose") }, onView = view) }
                composable("home?m={m}") { e -> HomeScreen(vm, highlight = e.arguments?.getString("m"), onOpenWeekend = openWeekend, onOpenChat = { openChat = it }, onAllChats = { nav.navigate("chats") { popUpTo("home"); launchSingleTop = true } }, onCompose = { nav.navigate("compose") }, onView = view) }
                composable("schedule") { ScheduleScreen(isAdmin = vm.me?.isAdmin == true, onOpenWeekend = openWeekend, onArchive = { nav.navigate("archive") }) }
                composable("weekend/{id}") { e -> WeekendScreen(vm, e.arguments?.getString("id") ?: "", view) }
                composable("chats") { ChatsScreen(vm, page = chatsPage, onPage = { chatsPage = it }, onOpen = { openChat = it }, onNewChat = { nav.navigate("newchat") }, onOpenWeekend = openWeekend) }
                // Made or chosen from a form: back to the chats tab, with the chat floating over it.
                composable("newchat") { NewChatScreen(onNewGroup = { nav.navigate("newgroup") }) { id -> nav.popBackStack("chats", false); openChat = id } }
                composable("newgroup") { NewGroupScreen { id -> nav.popBackStack("chats", false); openChat = id } }
                composable("group/{id}") { e -> GroupScreen(vm, e.arguments?.getString("id") ?: "", onOpenChat = { nav.popBackStack("chats", false); openChat = it }, onLeft = { openChat = null; nav.navigate("chats") { popUpTo("home") } }) { title = it } }
                composable("chatprofile/{id}") { e -> ChatProfileScreen(e.arguments?.getString("id") ?: "") { title = it } }
                composable("compose") { ComposeScreen { nav.popBackStack(); vm.changed() } }
                composable("people") { PeopleScreen(vm.me, onOpen = { nav.navigate("person/$it") }, onAdd = { nav.navigate("newperson") }, onEmail = { g -> nav.navigate(if (g == null) "email" else "email?group=$g") }) }
                composable("person/{id}") { e -> PersonScreen(vm.me, e.arguments?.getString("id") ?: "", onOpenChat = { openChat = it }) { title = it } }
                composable("newperson") { NewPersonScreen(vm.me) { id -> nav.navigate("person/$id") { popUpTo("people") } } }
                composable("email") { EmailScreen(null) { nav.popBackStack() } }
                composable("email?group={group}") { e -> EmailScreen(e.arguments?.getString("group")) { nav.popBackStack() } }
                composable("account") {
                    AccountScreen(
                        vm,
                        onScan = { nav.navigate("scanner") },
                        onArchive = { nav.navigate("archive") },
                        onDetails = { nav.navigate("details") },
                        onStorage = { nav.navigate("storage") },
                        onSettings = { nav.navigate("settings") },
                        onAbout = { nav.navigate("about") },
                    )
                }
                page("details") { AccountDetailsScreen(vm) }
                page("storage") { StorageScreen() }
                page("settings") { SettingsScreen() }
                page("about") { AboutScreen(vm, onChangelog = { nav.navigate("changelog") }, onLegal = { nav.navigate("legal/$it") }) }
                page("changelog") { ChangelogScreen() }
                page("legal/{doc}") { e -> LegalScreen(e.arguments?.getString("doc") ?: "privacy", onOpen = { nav.navigate("legal/$it") }, onTitle = { title = it }) }
                page("archive") { ArchiveScreen { nav.navigate("archive/$it") } }
                page("archive/{id}") { e -> SeasonArchiveScreen(vm, e.arguments?.getString("id") ?: "", onView = view, onDeleted = { nav.popBackStack() }) { title = it } }
                page("scanner") { ScannerScreen(onOpenChat = { openChat = it }) }
                composable("verify/{token}") { e -> ScannerScreen(initialToken = e.arguments?.getString("token"), onOpenChat = { openChat = it }) }
                composable("pdf") { pdf?.let { PdfScreen(it) } }
                composable("image") { image?.let { ImageScreen(it) } }
                composable("gallery") {
                    gallery?.let { g ->
                        GalleryScreen(
                            g,
                            onView = view,
                            onSelection = { gallerySelection = it },
                            onChanged = vm::changed,
                            // Only if the gallery is still what is showing: the delete may finish after a back press.
                            onDone = { if (nav.currentDestination?.route == "gallery") nav.popBackStack() },
                        )
                    }
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

    // The chat, floating over everything: in from the right, out to the right. Back closes it.
    if (!chatCovered) {
        AnimatedVisibility(
            visible = openChat != null,
            enter = slideInHorizontally(tween(220)) { it },
            exit = slideOutHorizontally(tween(200)) { it },
        ) {
            val id = openChat ?: lastChat
            if (id != null) {
                BackHandler(enabled = openChat != null) { openChat = null }
                // The name and photo are there from the first frame: from the phone's copy of the chat, or its row in the list.
                val who = chatWith ?: remember(id) {
                    app.chatCache.peek(id)?.let { it.other ?: it.group?.asOther() } ?: app.chatCache.peekList()?.find { it.id == id }?.other
                }
                Column(Modifier.fillMaxSize().background(Night)) {
                    val bar = selection
                    if (bar != null) SelectionTopBar(bar) else TopBar(
                        title = who?.name ?: "",
                        onBack = { openChat = null },
                        onOpenWeekend = openWeekend,
                        photo = who?.let { w -> { Avatar(app.api.absolute(w.photoUrl), w.name, 36) } },
                        onTitleClick = { nav.navigate(if (who?.role == "group") "group/$id" else "chatprofile/$id") },
                        menu = listOf("Search messages" to { chatSearch = true }) + (if (chatCanExport) listOf("Export chat" to { chatExport++ }) else emptyList()),
                    )
                    Box(Modifier.weight(1f)) {
                        ChatScreen(vm, id, view, onSelection = { selection = it }, searchOpen = chatSearch, onSearchClose = { chatSearch = false }, exportTick = chatExport, onCanExport = { chatCanExport = it }, onOpenChat = { openChat = it }) { chatWith = it }
                    }
                }
            }
        }
    }
    vm.popup?.let { event ->
        Box(Modifier.align(Alignment.TopCenter)) {
            PopupBubble(event, onOpen = { link -> vm.dismissPopup(); Links.route(link)?.let(go) }, onDismiss = vm::dismissPopup)
        }
    }
    }
}

/** Open a link, or do nothing if no browser is installed. */
fun UriHandler.openSafely(url: String) {
    runCatching { openUri(url) }
}

/** Pages opened from the Account tab (and their own pages): they fly in from the right over the screen under them. */
private val slidingPages = setOf("details", "storage", "settings", "about", "changelog", "legal/{doc}", "archive", "archive/{id}", "scanner")

private fun NavGraphBuilder.page(route: String, content: @Composable (NavBackStackEntry) -> Unit) = composable(
    route,
    enterTransition = { slideInHorizontally(tween(220)) { it } },
    popExitTransition = { slideOutHorizontally(tween(200)) { it } },
) { e ->
    // Solid, so the screen underneath doesn't show through while it slides.
    Box(Modifier.fillMaxSize().background(Night)) { content(e) }
}
