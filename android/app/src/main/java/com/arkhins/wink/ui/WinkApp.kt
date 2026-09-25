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
import androidx.navigation.NavController
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
    // The chats tab: 0 is the chat list, 1 the channels; the header switch and the swipe both move it.
    var chatsPage by remember { mutableIntStateOf(0) }
    var pdf by remember { mutableStateOf<SavedDocument?>(null) }
    var image by remember { mutableStateOf<FileInfo?>(null) }
    // A grid's photos, opened from it; while some are picked there the header is the selection bar.
    var gallery by remember { mutableStateOf<FileView.Gallery?>(null) }
    var gallerySelection by remember { mutableStateOf<SelectionBar?>(null) }

    val back: () -> Unit = { nav.popBackStack() }
    /** A chat opens on top of what is showing; from inside a chat (a forward), it takes that chat's place. */
    val openChat: (String) -> Unit = { id ->
        val top = nav.currentBackStackEntry
        val inChat = top?.destination?.route == "chat/{id}"
        if (!(inChat && top?.arguments?.getString("id") == id)) {
            nav.navigate("chat/$id") { if (inChat) popUpTo("chat/{id}") { inclusive = true } }
        }
    }
    /** Go where a link or a saved route says. */
    val go: (String) -> Unit = { r -> if (r.startsWith("chat/")) openChat(r.removePrefix("chat/")) else runCatching { nav.navigate(r) } }
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
            runCatching { nav.navigate(saved) { launchSingleTop = true } }
        }
    }
    DisposableEffect(nav) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, arguments ->
            val pattern = destination.route ?: return@OnDestinationChangedListener
            // Screens that make sense to come back to; not viewers or forms.
            val concrete = when {
                pattern in setOf("home", "schedule", "chats", "people", "account") -> pattern
                pattern.startsWith("weekend/") || pattern.startsWith("person/") || pattern.startsWith("chat/") ->
                    pattern.replace("{id}", arguments?.getString("id") ?: return@OnDestinationChangedListener)
                else -> return@OnDestinationChangedListener
            }
            restoreScope.launch { app.session.saveRoute(concrete) }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }

    val openWeekend: (String) -> Unit = { nav.open("weekend/$it") }
    val view: (FileView) -> Unit = {
        when (it) {
            is FileView.Pdf -> { pdf = it.doc; nav.open("pdf") }
            is FileView.Image -> { image = it.file; nav.open("image") }
            is FileView.Gallery -> { gallery = it; gallerySelection = null; nav.open("gallery") }
        }
    }

    /** A tab: its header, its page, and the footer. */
    @Composable
    fun Tab(current: String, title: String, center: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) =
        Screen(title, onBack = null, onOpenWeekend = openWeekend, center = center, footer = {
            // A tab always shows its own page: everything above Home is
            // dropped first, nothing is restored (a chat opened from a popup
            // would otherwise come back on top of Home).
            BottomNav(
                current = current,
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
        }, content = content)

    /** A screen opened on top: its own header with a back arrow, no footer. */
    @Composable
    fun Pushed(title: String, showCountdown: Boolean = true, header: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) =
        Screen(title, onBack = back, onOpenWeekend = openWeekend, showCountdown = showCountdown, header = header, content = content)

    Box(Modifier.fillMaxSize()) {
        // Tabs swap in place, footer still. Anything else flies in from the right, header and all, over
        // the screen it was opened from, which stays exactly as it was and is there again on the way back.
        NavHost(
            nav,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
            enterTransition = { if (!targetState.isTab()) slideInHorizontally(tween(220)) { it } else if (initialState.isTab()) EnterTransition.None else fadeIn(tween(120)) },
            exitTransition = { if (!targetState.isTab()) ExitTransition.KeepUntilTransitionsFinished else if (initialState.isTab()) ExitTransition.None else fadeOut(tween(90)) },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { if (initialState.isTab()) ExitTransition.None else slideOutHorizontally(tween(200)) { it } },
        ) {
            composable("home") { Tab("home", "Wink") { HomeScreen(vm, highlight = null, onOpenWeekend = openWeekend, onOpenChat = openChat, onAllChats = { nav.navigate("chats") { popUpTo("home"); launchSingleTop = true } }, onCompose = { nav.open("compose") }, onView = view) } }
            composable("home?m={m}") { e -> Tab("home", "Wink") { HomeScreen(vm, highlight = e.arguments?.getString("m"), onOpenWeekend = openWeekend, onOpenChat = openChat, onAllChats = { nav.navigate("chats") { popUpTo("home"); launchSingleTop = true } }, onCompose = { nav.open("compose") }, onView = view) } }
            composable("schedule") { Tab("schedule", "Schedule") { ScheduleScreen(isAdmin = vm.me?.isAdmin == true, onOpenWeekend = openWeekend, onArchive = { nav.open("archive") }) } }
            composable("chats") { Tab("chats", "Chats", center = { ChatsHeader(chatsPage) { chatsPage = it } }) { ChatsScreen(vm, page = chatsPage, onPage = { chatsPage = it }, onOpen = openChat, onNewChat = { nav.open("newchat") }, onOpenWeekend = openWeekend) } }
            composable("people") { Tab("people", "People") { PeopleScreen(vm.me, onOpen = { nav.open("person/$it") }, onAdd = { nav.open("newperson") }, onEmail = { g -> nav.open(if (g == null) "email" else "email?group=$g") }) } }
            composable("account") {
                Tab("account", "Account") {
                    AccountScreen(
                        vm,
                        onScan = { nav.open("scanner") },
                        onArchive = { nav.open("archive") },
                        onDetails = { nav.open("details") },
                        onStorage = { nav.open("storage") },
                        onSettings = { nav.open("settings") },
                        onAbout = { nav.open("about") },
                    )
                }
            }

            composable("chat/{id}") { e ->
                val id = e.arguments?.getString("id") ?: ""
                var chatWith by remember { mutableStateOf<OtherUser?>(null) }
                // Messages long-pressed in a chat: the header turns into the selection bar.
                var selection by remember { mutableStateOf<SelectionBar?>(null) }
                // The chat header's ⋮ menu: a search bar in the chat, or an export of it.
                var chatSearch by remember { mutableStateOf(false) }
                var chatExport by remember { mutableIntStateOf(0) }
                var chatCanExport by remember { mutableStateOf(true) }
                // The name and photo are there from the first frame: from the phone's copy of the chat, or its row in the list.
                val who = chatWith ?: remember(id) {
                    app.chatCache.peek(id)?.let { it.other ?: it.group?.asOther() } ?: app.chatCache.peekList()?.find { it.id == id }?.other
                }
                Pushed(who?.name ?: "", header = {
                    val bar = selection
                    if (bar != null) SelectionTopBar(bar) else TopBar(
                        title = who?.name ?: "",
                        onBack = back,
                        onOpenWeekend = openWeekend,
                        photo = who?.let { w -> { Avatar(app.api.absolute(w.photoUrl), w.name, 36) } },
                        onTitleClick = { nav.open(if (who?.role == "group") "group/$id" else "chatprofile/$id") },
                        menu = listOf("Search messages" to { chatSearch = true }) + (if (chatCanExport) listOf("Export chat" to { chatExport++ }) else emptyList()),
                    )
                }) {
                    ChatScreen(vm, id, view, onSelection = { selection = it }, searchOpen = chatSearch, onSearchClose = { chatSearch = false }, exportTick = chatExport, onCanExport = { chatCanExport = it }, onOpenChat = openChat) { chatWith = it }
                }
            }
            // Made or chosen from a form: back to the chats tab, with the chat on top of it.
            composable("newchat") { Pushed("New chat") { NewChatScreen(onNewGroup = { nav.open("newgroup") }) { id -> nav.popBackStack("chats", false); openChat(id) } } }
            composable("newgroup") { Pushed("New group") { NewGroupScreen { id -> nav.popBackStack("chats", false); openChat(id) } } }
            composable("group/{id}") { e ->
                var t by remember { mutableStateOf("") }
                Pushed(t) { GroupScreen(vm, e.arguments?.getString("id") ?: "", onOpenChat = { nav.popBackStack("chats", false); openChat(it) }, onLeft = { nav.navigate("chats") { popUpTo("home") } }) { t = it } }
            }
            composable("chatprofile/{id}") { e ->
                var t by remember { mutableStateOf("") }
                Pushed(t) { ChatProfileScreen(e.arguments?.getString("id") ?: "") { t = it } }
            }
            composable("compose") { Pushed("New message") { ComposeScreen { nav.popBackStack(); vm.changed() } } }
            composable("weekend/{id}") { e -> Pushed("Race weekend") { WeekendScreen(vm, e.arguments?.getString("id") ?: "", view) } }
            composable("person/{id}") { e ->
                var t by remember { mutableStateOf("") }
                Pushed(t) { PersonScreen(vm.me, e.arguments?.getString("id") ?: "", onOpenChat = openChat) { t = it } }
            }
            composable("newperson") { Pushed("Add person") { NewPersonScreen(vm.me) { id -> nav.navigate("person/$id") { popUpTo("people") } } } }
            composable("email") { Pushed("Email") { EmailScreen(null) { nav.popBackStack() } } }
            composable("email?group={group}") { e -> Pushed("Email") { EmailScreen(e.arguments?.getString("group")) { nav.popBackStack() } } }

            composable("details") { Pushed("Account") { AccountDetailsScreen(vm) } }
            composable("storage") { Pushed("Storage") { StorageScreen() } }
            composable("settings") { Pushed("Settings") { SettingsScreen() } }
            composable("about") { Pushed("About") { AboutScreen(vm, onChangelog = { nav.open("changelog") }, onLegal = { nav.open("legal/$it") }) } }
            composable("changelog") { Pushed("What's new") { ChangelogScreen() } }
            composable("legal/{doc}") { e ->
                var t by remember { mutableStateOf("") }
                Pushed(t) { LegalScreen(e.arguments?.getString("doc") ?: "privacy", onOpen = { nav.open("legal/$it") }, onTitle = { t = it }) }
            }
            composable("archive") { Pushed("Archive") { ArchiveScreen { nav.open("archive/$it") } } }
            composable("archive/{id}") { e ->
                var t by remember { mutableStateOf("Season") }
                Pushed(t) { SeasonArchiveScreen(vm, e.arguments?.getString("id") ?: "", onView = view, onDeleted = back) { t = it } }
            }
            composable("scanner") { Pushed("Verify") { ScannerScreen(onOpenChat = openChat) } }
            composable("verify/{token}") { e -> Pushed("Verify") { ScannerScreen(initialToken = e.arguments?.getString("token"), onOpenChat = openChat) } }

            composable("pdf") { Pushed(pdf?.name ?: "Document", showCountdown = false) { pdf?.let { PdfScreen(it) } } }
            composable("image") { Pushed(image?.name ?: "Photo", showCountdown = false) { image?.let { ImageScreen(it) } } }
            composable("gallery") {
                // Who sent the photos and when, as WhatsApp heads them: "You · 8:16 pm".
                val heading = gallery?.message?.let { m -> "${if (m.mine) "You" else m.sender?.name ?: "Wink"} · ${localTime(m.createdAt)}" } ?: "Photos"
                Pushed(heading, showCountdown = false, header = gallerySelection?.let { bar -> { SelectionTopBar(bar) } }) {
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

        vm.popup?.let { event ->
            Box(Modifier.align(Alignment.TopCenter)) {
                PopupBubble(event, onOpen = { link -> vm.dismissPopup(); Links.route(link)?.let(go) }, onDismiss = vm::dismissPopup)
            }
        }
    }
}

/**
 * Open a screen from a tap. Taps while a screen is still sliding in are ignored (the screen they were
 * made on is no longer the settled one), and a screen already on top is never stacked on itself, so
 * tapping Settings three times, or the countdown on the race weekend page, opens it once.
 */
private fun NavController.open(route: String) {
    val top = currentBackStackEntry
    if (top != null && !top.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    // The exact screen on top ("weekend/42", not just any weekend): Terms can still open Privacy.
    val showing = top?.destination?.route?.let { pattern ->
        Regex("\\{(\\w+)\\}").replace(pattern) { m -> top.arguments?.getString(m.groupValues[1]) ?: "" }
    }
    if (showing == route) return
    navigate(route)
}

/** Open a link, or do nothing if no browser is installed. */
fun UriHandler.openSafely(url: String) {
    runCatching { openUri(url) }
}

private val TABS = setOf("home", "home?m={m}", "schedule", "chats", "people", "account")

private fun NavBackStackEntry.isTab() = destination.route in TABS

/**
 * One screen, whole: its header, its page and (on a tab) the footer, on a
 * solid background so nothing underneath shows through while it slides.
 */
@Composable
private fun Screen(
    title: String,
    onBack: (() -> Unit)?,
    onOpenWeekend: (String) -> Unit,
    showCountdown: Boolean = true,
    center: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Night)) {
        if (header != null) header()
        else TopBar(title = title, onBack = onBack, onOpenWeekend = onOpenWeekend, showCountdown = showCountdown, center = center)
        Box(Modifier.weight(1f)) { content() }
        footer?.invoke()
    }
}
