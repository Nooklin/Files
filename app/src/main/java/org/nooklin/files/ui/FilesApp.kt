package org.nooklin.files.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nooklin.files.fs.MediaGroup
import org.nooklin.files.cloud.ServerType
import org.nooklin.files.ui.media.MediaScreen
import org.nooklin.files.ui.media.MediaViewState
import androidx.compose.foundation.lazy.LazyListState
import org.nooklin.files.ui.viewer.ViewerHost
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.DisposableEffect
import org.nooklin.files.ui.common.PermissionNeededScreen
import org.nooklin.files.ui.common.PermissionsIntroDialog
import org.nooklin.files.ui.common.notificationsAllowed
import org.nooklin.files.ui.common.openAllFilesSettings
import org.nooklin.files.ui.settings.SettingsPermissionsScreen
import org.nooklin.files.R
import org.nooklin.files.data.AppThemeMode
import org.nooklin.files.fs.OpError
import org.nooklin.files.ui.common.NooklinMenu
import org.nooklin.files.ui.common.shareFiles
import org.nooklin.files.ui.editor.EditorScreen
import org.nooklin.files.ui.files.ConfirmDialog
import org.nooklin.files.ui.files.ConflictDialog
import org.nooklin.files.ui.files.DeleteDialog
import org.nooklin.files.ui.files.ErrorsDialog
import org.nooklin.files.ui.files.FilesScreen
import org.nooklin.files.ui.files.NameDialog
import org.nooklin.files.ui.files.NameDialogKind
import org.nooklin.files.ui.files.PropertiesDialog
import org.nooklin.files.ui.files.BatchRenameDialog
import org.nooklin.files.ui.files.batchNames
import org.nooklin.files.ui.files.NameDialogState
import org.nooklin.files.ui.files.PasswordDialog
import org.nooklin.files.ui.files.itemsText
import org.nooklin.files.ui.navigation.ActionPill
import org.nooklin.files.ui.navigation.AppBottomBar
import org.nooklin.files.ui.navigation.ClipboardPill
import org.nooklin.files.ui.navigation.MiniPlayer
import org.nooklin.files.ui.navigation.OperationPill
import org.nooklin.files.ui.navigation.PillButton
import org.nooklin.files.ui.home.HomeScreen
import org.nooklin.files.ui.navigation.Tab
import org.nooklin.files.ui.recents.RecentsScreen
import org.nooklin.files.ui.common.AppLockGate
import org.nooklin.files.ui.apps.AppManagerScreen
import org.nooklin.files.ui.servers.ServerScreen
import org.nooklin.files.ui.settings.SettingsServerEditScreen
import org.nooklin.files.ui.settings.SettingsServersScreen
import org.nooklin.files.ui.storage.StorageScreen
import org.nooklin.files.ui.settings.SettingsAboutScreen
import org.nooklin.files.ui.settings.SettingsChangelogScreen
import org.nooklin.files.ui.settings.SettingsHelpScreen
import org.nooklin.files.ui.settings.SettingsHowToScreen
import org.nooklin.files.ui.settings.SettingsPrivacyScreen
import org.nooklin.files.ui.settings.SettingsAppearanceScreen
import org.nooklin.files.ui.settings.SettingsNextcloudScreen
import org.nooklin.files.ui.settings.SettingsPage
import org.nooklin.files.cloud.DavUrls
import org.nooklin.files.ui.navigation.BusyPill
import org.nooklin.files.ui.nextcloud.NextcloudScreen
import org.nooklin.files.ui.settings.SettingsScreen
import org.nooklin.files.ui.theme.NooklinTheme
import org.nooklin.files.ui.trash.TrashScreen
import java.io.File

/** What the Home tab shows: the Home screen itself, or a screen opened from it. */
private enum class HomeView { LIST, BROWSER, TRASH, NEXTCLOUD, SERVER, APPS, STORAGE, MEDIA }

@Composable
fun FilesApp(
    storageGranted: Boolean,
    shortcut: ShortcutTarget?,
    onShortcutHandled: () -> Unit,
    openFolder: String? = null,
    onFolderHandled: () -> Unit = {},
    pick: PickMode? = null,
    vm: FilesViewModel = viewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dark = when (settings.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.BLACK -> true
    }
    NooklinTheme(
        darkTheme = dark,
        contrastColor = settings.contrastColor,
        appFont = settings.appFont,
        blackMode = settings.themeMode == AppThemeMode.BLACK,
        colorful = settings.colorful
    ) {
        // Screenshots and the recent apps preview are blocked while this is on
        val context = LocalContext.current
        LaunchedEffect(settings.disableScreenshots) {
            (context as? Activity)?.window?.let { window ->
                if (settings.disableScreenshots) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        val appLock by vm.appLock.collectAsStateWithLifecycle()
        val unlocked by vm.unlocked.collectAsStateWithLifecycle()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                // The stored choice has not loaded yet: show nothing, so the content never flashes before the lock
                appLock == null -> Unit
                appLock == true && !unlocked -> AppLockGate(onAuthenticated = { vm.unlock() }, onClose = { (context as? Activity)?.finish() })
                else -> {
                    // While another app has asked for a file the app shows a bar to cancel, above the normal screens
                    SideEffect { vm.pickCallback = pick?.onPick }
                    Column(Modifier.fillMaxSize()) {
                        if (pick != null) PickBar(pick.onCancel)
                        Box(
                            Modifier
                                .weight(1f)
                                .then(if (pick != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)
                        ) {
                            MainContent(vm, storageGranted, shortcut, onShortcutHandled, openFolder, onFolderHandled)
                        }
                    }
                }
            }
        }
    }
}

/** What another app asked for: choose one file. The app hands the chosen path to onPick. */
class PickMode(val onPick: (String) -> Unit, val onCancel: () -> Unit)

/** The strip above the app while a file is being chosen for another app. */
@Composable
private fun PickBar(onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(R.string.pick_title), fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
    }
}

@Composable
private fun MainContent(
    vm: FilesViewModel,
    granted: Boolean,
    shortcut: ShortcutTarget?,
    onShortcutHandled: () -> Unit,
    openFolder: String?,
    onFolderHandled: () -> Unit
) {
    val context = LocalContext.current
    // Without All files access the panes cannot read the phone, so they start when the permission is there
    LaunchedEffect(granted) { if (granted) vm.start() }

    // The permissions are asked once, with an explanation, the first time the app starts
    val introDone by vm.permissionsIntroDone.collectAsStateWithLifecycle()
    var notificationsOk by remember { mutableStateOf(notificationsAllowed(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) notificationsOk = notificationsAllowed(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val askFilesAfterNotifications = remember { mutableStateOf(false) }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOk = notificationsAllowed(context)
        if (askFilesAfterNotifications.value) openAllFilesSettings(context)
    }
    LaunchedEffect(introDone, granted, notificationsOk) {
        // Everything is allowed already (an update, or given in the settings): nothing to explain
        if (introDone == false && granted && notificationsOk) vm.setPermissionsIntroDone()
    }
    if (introDone == false && !(granted && notificationsOk)) {
        PermissionsIntroDialog(
            needFiles = !granted,
            needNotifications = !notificationsOk,
            onContinue = {
                vm.setPermissionsIntroDone()
                askFilesAfterNotifications.value = !granted
                if (!notificationsOk) notificationRequest.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else if (!granted) openAllFilesSettings(context)
            },
            onDecline = { vm.setPermissionsIntroDone() }
        )
    }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var homeView by rememberSaveable { mutableStateOf(HomeView.LIST) }
    // The media screen's choices stay here, the screen itself is gone while a file is open
    val mediaView = remember { MediaViewState(LazyListState()) }
    var settingsPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // The server that the form is changing, empty for a new one
    var editingServer by rememberSaveable { mutableStateOf("") }
    var serverNameDialog by remember { mutableStateOf<NameDialogState?>(null) }
    var serverDeleteDialog by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf<NameDialogState?>(null) }
    var propertiesPath by remember { mutableStateOf<String?>(null) }
    var batchRenamePaths by remember { mutableStateOf<List<String>?>(null) }
    var deleteDialog by remember { mutableStateOf(false) }
    var trashDeleteDialog by remember { mutableStateOf(false) }
    var emptyTrashDialog by remember { mutableStateOf(false) }
    var trashRemote by rememberSaveable { mutableStateOf(false) }
    var ncNameDialog by remember { mutableStateOf<NameDialogState?>(null) }
    var ncDeleteDialog by remember { mutableStateOf(false) }
    var errorDialog by remember { mutableStateOf<List<OpError>?>(null) }
    var overlayHeightPx by remember { mutableIntStateOf(0) }

    val dual by vm.dualPane.collectAsStateWithLifecycle()
    val active by vm.activePane.collectAsStateWithLifecycle()
    val pane0 by vm.panes[0].collectAsStateWithLifecycle()
    val pane1 by vm.panes[1].collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val clipboard by vm.clipboard.collectAsStateWithLifecycle()
    val opState by vm.operations.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val trashSelected by vm.trashSelected.collectAsStateWithLifecycle()
    val editorPath by vm.editorPath.collectAsStateWithLifecycle()
    val viewer by vm.viewer.collectAsStateWithLifecycle()
    val backgroundPlay by vm.backgroundPlay.collectAsStateWithLifecycle()
    val savedServers by vm.servers.servers.collectAsStateWithLifecycle()
    val ncAccount by vm.nc.account.collectAsStateWithLifecycle()
    val secondRemote by vm.secondRemote.collectAsStateWithLifecycle()
    val passwordRequest by vm.passwordRequest.collectAsStateWithLifecycle()
    val ncRemote by vm.nc.remote.collectAsStateWithLifecycle()
    val srvRemote by vm.servers.remote.collectAsStateWithLifecycle()
    val ncTrash by vm.nc.trash.collectAsStateWithLifecycle()
    val appLock by vm.appLock.collectAsStateWithLifecycle()

    val activeIndex = if (dual) active else 0
    val activeUi = if (activeIndex == 0) pane0 else pane1
    val browsing = tab == Tab.HOME && homeView == HomeView.BROWSER
    // The second pane shows the Nextcloud server and is the active one: the selection then lives on the server
    val paneRemote = browsing && dual && active == 1 && secondRemote && ncAccount != null
    val filesSelection = browsing && search == null && !paneRemote && activeUi.selected.isNotEmpty()
    val filesSelecting = browsing && search == null && !paneRemote && (activeUi.selected.isNotEmpty() || activeUi.selecting)
    val remoteTrashShown = trashRemote && ncAccount != null
    val trashSelection = tab == Tab.HOME && homeView == HomeView.TRASH &&
        (if (remoteTrashShown) ncTrash.selected else trashSelected).isNotEmpty()
    val ncSelection = tab == Tab.HOME && (homeView == HomeView.NEXTCLOUD || paneRemote) && ncRemote.selected.isNotEmpty()
    val srvSelection = tab == Tab.HOME && homeView == HomeView.SERVER && srvRemote.selected.isNotEmpty()
    val srvSelecting = tab == Tab.HOME && homeView == HomeView.SERVER && (srvRemote.selected.isNotEmpty() || srvRemote.selecting)
    val ncSelecting = tab == Tab.HOME && (homeView == HomeView.NEXTCLOUD || paneRemote) &&
        (ncRemote.selected.isNotEmpty() || ncRemote.selecting)
    val bottomPadding = with(LocalDensity.current) { overlayHeightPx.toDp() }

    // Notifications are only for the progress of a copy, asked once
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !vm.askedNotifications()) {
            vm.markAskedNotifications()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        vm.operations.finished.collect { summary ->
            if (summary.errors.isNotEmpty()) errorDialog = summary.errors
        }
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(Unit) {
        vm.nc.notices.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(Unit) {
        vm.servers.notices.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    // Disconnecting while a Nextcloud screen is open: go back to Home
    LaunchedEffect(ncAccount) {
        if (ncAccount == null) {
            if (homeView == HomeView.NEXTCLOUD) homeView = HomeView.LIST
            vm.setSecondRemote(false)
        }
    }

    // Back and the arrow in the title bar do the same: undo the most recent step
    val goBack: () -> Unit = {
        when {
            search != null -> vm.stopSearch()
            filesSelecting -> vm.clearSelection(activeIndex)
            trashSelection -> if (remoteTrashShown) vm.nc.clearTrashSelection() else vm.clearTrashSelection()
            ncSelecting -> vm.nc.clearSelection()
            srvSelecting -> vm.servers.clearSelection()
            tab == Tab.SETTINGS && settingsPage == SettingsPage.SERVER_EDIT -> settingsPage = SettingsPage.SERVERS
            tab == Tab.SETTINGS && settingsPage == SettingsPage.HOWTO -> settingsPage = SettingsPage.HELP
            tab == Tab.SETTINGS && settingsPage == SettingsPage.CHANGELOG -> settingsPage = SettingsPage.ABOUT
            tab == Tab.SETTINGS && settingsPage != null -> settingsPage = null
            tab != Tab.HOME -> {
                tab = Tab.HOME
                settingsPage = null
            }
            homeView == HomeView.TRASH -> homeView = HomeView.LIST
            homeView == HomeView.NEXTCLOUD -> {
                if (vm.nc.canGoUp()) vm.nc.goUp() else homeView = HomeView.LIST
            }
            homeView == HomeView.SERVER -> {
                if (vm.servers.canGoUp()) {
                    vm.servers.goUp()
                } else {
                    vm.servers.closeServer()
                    homeView = HomeView.LIST
                }
            }
            homeView == HomeView.APPS || homeView == HomeView.STORAGE || homeView == HomeView.MEDIA -> homeView = HomeView.LIST
            homeView == HomeView.BROWSER -> {
                if (activeUi.canGoBack) vm.goBack(activeIndex) else homeView = HomeView.LIST
            }
        }
    }
    BackHandler(enabled = search != null || tab != Tab.HOME || homeView != HomeView.LIST || settingsPage != null) {
        goBack()
    }

    // Launcher shortcuts: the options on the app icon
    ShortcutsEffect(
        hasNextcloud = ncAccount != null,
        hasFtp = savedServers.any { it.type == ServerType.FTP },
        hasSmb = savedServers.any { it.type == ServerType.SMB }
    )
    LaunchedEffect(shortcut, ncAccount, savedServers) {
        val target = shortcut ?: return@LaunchedEffect
        // Nextcloud and the servers load a moment after the start, so a shortcut for them waits for that
        var done = true
        when (target) {
            ShortcutTarget.RECENTS -> {
                settingsPage = null
                tab = Tab.RECENTS
            }
            ShortcutTarget.APPS -> {
                settingsPage = null
                tab = Tab.HOME
                homeView = HomeView.APPS
            }
            ShortcutTarget.NEXTCLOUD -> if (ncAccount != null) {
                settingsPage = null
                tab = Tab.HOME
                vm.nc.open("")
                homeView = HomeView.NEXTCLOUD
            } else done = false
            ShortcutTarget.FTP, ShortcutTarget.SMB -> {
                val wanted = if (target == ShortcutTarget.FTP) ServerType.FTP else ServerType.SMB
                val server = savedServers.firstOrNull { it.type == wanted }
                if (server != null) {
                    settingsPage = null
                    tab = Tab.HOME
                    vm.servers.openServer(server.id)
                    homeView = HomeView.SERVER
                } else done = false
            }
        }
        if (done) onShortcutHandled()
    }
    // Another app asked to show a folder
    LaunchedEffect(openFolder) {
        val path = openFolder ?: return@LaunchedEffect
        settingsPage = null
        tab = Tab.HOME
        vm.setActivePane(activeIndex)
        vm.openFromHome(activeIndex, path)
        homeView = HomeView.BROWSER
        onFolderHandled()
    }
    // A shortcut for something that is not set up (any more) is dropped after a short wait
    LaunchedEffect(shortcut) {
        if (shortcut != null) {
            kotlinx.coroutines.delay(4000)
            onShortcutHandled()
        }
    }

    val viewing = viewer
    val editing = editorPath
    if (viewing != null) {
        // The viewers cover the whole app, including the bottom bar
        ViewerHost(viewing, backgroundPlay, { vm.setBackgroundPlay(it) }, { vm.closeViewer() })
    } else if (editing != null) {
        // The editor covers the whole app, including the bottom bar
        EditorScreen(editing) { vm.closeEditor() }
    } else Box(Modifier.fillMaxSize()) {
        when (tab) {
            Tab.HOME -> when (homeView) {
                HomeView.LIST -> HomeScreen(
                    vm = vm,
                    granted = granted,
                    bottomPadding = bottomPadding,
                    onOpen = { path ->
                        vm.setActivePane(activeIndex)
                        vm.openFromHome(activeIndex, path)
                        homeView = HomeView.BROWSER
                    },
                    onOpenNextcloud = {
                        vm.nc.open("")
                        homeView = HomeView.NEXTCLOUD
                    },
                    onOpenServer = { id ->
                        vm.servers.openServer(id)
                        homeView = HomeView.SERVER
                    },
                    onOpenApps = { homeView = HomeView.APPS },
                    onOpenStorage = { homeView = HomeView.STORAGE },
                    onOpenMedia = { homeView = HomeView.MEDIA },
                    onOpenTrash = { homeView = HomeView.TRASH }
                )

                HomeView.BROWSER -> if (!granted) PermissionNeededScreen(stringResource(R.string.tab_home), goBack) else FilesScreen(
                    vm = vm,
                    bottomPadding = bottomPadding,
                    onBack = goBack,
                    onNameDialog = { nameDialog = it },
                    onNcNewFolder = { ncNameDialog = NameDialogState(NameDialogKind.NEW_FOLDER) }
                )
                HomeView.TRASH -> if (!granted) PermissionNeededScreen(stringResource(R.string.location_recently_deleted), goBack) else TrashScreen(
                    vm = vm,
                    bottomPadding = bottomPadding,
                    remote = trashRemote,
                    onRemoteChange = {
                        vm.clearTrashSelection()
                        vm.nc.clearTrashSelection()
                        trashRemote = it
                    },
                    onBack = goBack,
                    onEmptyTrash = { emptyTrashDialog = true }
                )

                HomeView.NEXTCLOUD -> NextcloudScreen(vm, bottomPadding, goBack) {
                    ncNameDialog = NameDialogState(NameDialogKind.NEW_FOLDER)
                }

                HomeView.SERVER -> ServerScreen(vm, bottomPadding, goBack) {
                    serverNameDialog = NameDialogState(NameDialogKind.NEW_FOLDER)
                }

                HomeView.APPS -> AppManagerScreen(bottomPadding, goBack)

                HomeView.STORAGE -> StorageScreen(bottomPadding, granted, goBack) { group ->
                    mediaView.group = group
                    mediaView.listState.requestScrollToItem(0)
                    homeView = HomeView.MEDIA
                }

                HomeView.MEDIA -> if (!granted) PermissionNeededScreen(stringResource(R.string.media_title), goBack) else MediaScreen(vm, mediaView, bottomPadding, goBack)
            }

            Tab.RECENTS -> if (!granted) PermissionNeededScreen(stringResource(R.string.tab_recents), null) else RecentsScreen(vm, bottomPadding) { path ->
                vm.setActivePane(activeIndex)
                vm.openFromHome(activeIndex, path)
                homeView = HomeView.BROWSER
                tab = Tab.HOME
            }
            Tab.SETTINGS -> when (settingsPage) {
                null -> SettingsScreen(vm, bottomPadding) { settingsPage = it }
                SettingsPage.APPEARANCE -> SettingsAppearanceScreen(
                    themeMode = settings.themeMode,
                    onThemeModeChange = vm::setThemeMode,
                    contrastColor = settings.contrastColor,
                    onContrastColorChange = vm::setContrastColor,
                    colorful = settings.colorful,
                    onColorfulChange = vm::setColorful,
                    showHidden = settings.showHidden,
                    onShowHiddenChange = vm::setShowHidden,
                    appFont = settings.appFont,
                    onAppFontChange = vm::setAppFont,
                    bottomPadding = bottomPadding,
                    onBack = { settingsPage = null }
                )

                SettingsPage.NEXTCLOUD -> SettingsNextcloudScreen(vm, bottomPadding) { settingsPage = null }

                SettingsPage.SERVERS -> SettingsServersScreen(
                    vm = vm,
                    bottomPadding = bottomPadding,
                    onAdd = {
                        editingServer = ""
                        settingsPage = SettingsPage.SERVER_EDIT
                    },
                    onEdit = {
                        editingServer = it
                        settingsPage = SettingsPage.SERVER_EDIT
                    },
                    onBack = { settingsPage = null }
                )

                SettingsPage.SERVER_EDIT -> SettingsServerEditScreen(
                    vm = vm,
                    serverId = editingServer,
                    bottomPadding = bottomPadding,
                    onDone = { settingsPage = SettingsPage.SERVERS },
                    onBack = { settingsPage = SettingsPage.SERVERS }
                )

                SettingsPage.PRIVACY -> SettingsPrivacyScreen(
                    appLock = appLock == true,
                    onAppLockChange = vm::setAppLock,
                    disableScreenshots = settings.disableScreenshots,
                    onDisableScreenshotsChange = vm::setDisableScreenshots,
                    shareEnabled = settings.shareEnabled,
                    onShareEnabledChange = vm::setShareEnabled,
                    bottomPadding = bottomPadding,
                    onBack = { settingsPage = null }
                )

                SettingsPage.PERMISSIONS -> SettingsPermissionsScreen(granted, notificationsOk, bottomPadding) { settingsPage = null }

                SettingsPage.HELP -> SettingsHelpScreen(bottomPadding, onOpenHowTo = { settingsPage = SettingsPage.HOWTO }) { settingsPage = null }

                SettingsPage.HOWTO -> SettingsHowToScreen(bottomPadding) { settingsPage = SettingsPage.HELP }

                SettingsPage.ABOUT -> SettingsAboutScreen(bottomPadding, onOpenChangelog = { settingsPage = SettingsPage.CHANGELOG }) { settingsPage = null }

                SettingsPage.CHANGELOG -> SettingsChangelogScreen(bottomPadding) { settingsPage = SettingsPage.ABOUT }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { overlayHeightPx = it.size.height }
                .navigationBarsPadding()
        ) {
            MiniPlayer(onOpen = { paths, index -> vm.openPlayer(paths, index) })
            opState?.let { OperationPill(it, onCancel = { vm.operations.cancelCurrent() }) }
            ncRemote.busy?.let { BusyPill(it, onCancel = { vm.nc.cancelAction() }) }
            if (homeView == HomeView.SERVER) srvRemote.busy?.let { BusyPill(it, onCancel = { vm.servers.cancelAction() }) }
            val clip = clipboard
            val clipView = tab == Tab.HOME && (browsing || homeView == HomeView.LIST || homeView == HomeView.NEXTCLOUD || homeView == HomeView.SERVER)
            if (clip != null && clipView && search == null && !filesSelection && !ncSelection && !srvSelection) {
                ClipboardPill(
                    text = stringResource(
                        if (clip.move) R.string.clipboard_move else R.string.clipboard_copy,
                        itemsText(clip.count)
                    ),
                    onPaste = when {
                        browsing -> ({ vm.paste(activeIndex) })
                        homeView == HomeView.NEXTCLOUD -> ({ vm.pasteToNextcloud() })
                        homeView == HomeView.SERVER -> ({ vm.pasteToServer() })
                        else -> null
                    },
                    onClear = { vm.clearClipboard() }
                )
            }
            when {
                filesSelection -> FilesSelectionBar(
                    vm = vm,
                    activeIndex = activeIndex,
                    ui = activeUi,
                    dual = dual,
                    shareEnabled = settings.shareEnabled,
                    onDelete = { deleteDialog = true },
                    onRename = { path -> nameDialog = NameDialogState(NameDialogKind.RENAME, path, File(path).name) },
                    onProperties = { propertiesPath = it },
                    onBatchRename = { batchRenamePaths = it }
                )

                ncSelection -> NextcloudSelectionBar(
                    vm = vm,
                    inPane = paneRemote,
                    onDelete = { ncDeleteDialog = true },
                    onRename = { path -> ncNameDialog = NameDialogState(NameDialogKind.RENAME, path, DavUrls.nameOf(path)) }
                )

                srvSelection -> ServerSelectionBar(
                    vm = vm,
                    onDelete = { serverDeleteDialog = true },
                    onRename = { path -> serverNameDialog = NameDialogState(NameDialogKind.RENAME, path, DavUrls.nameOf(path)) }
                )

                trashSelection -> ActionPill {
                    PillButton(
                        Icons.Rounded.Close,
                        stringResource(R.string.common_cancel),
                        { if (remoteTrashShown) vm.nc.clearTrashSelection() else vm.clearTrashSelection() }
                    )
                    PillButton(
                        Icons.Rounded.Restore,
                        stringResource(R.string.action_restore),
                        { if (remoteTrashShown) vm.nc.restoreSelectedTrash() else vm.restoreSelectedTrash() }
                    )
                    PillButton(Icons.Rounded.DeleteForever, stringResource(R.string.action_delete_permanently), { trashDeleteDialog = true })
                }

                else -> AppBottomBar(
                    selected = tab,
                    onSelect = {
                        // Tapping Home while on Home goes back to the Home screen itself
                        if (it == Tab.HOME && tab == Tab.HOME) homeView = HomeView.LIST
                        if (search != null) vm.stopSearch()
                        tab = it
                        if (it != Tab.SETTINGS) settingsPage = null
                    }
                )
            }
        }
    }

    propertiesPath?.let { PropertiesDialog(it) { propertiesPath = null } }
    batchRenamePaths?.let { paths ->
        BatchRenameDialog(
            items = paths.map { File(it) },
            onConfirm = { base ->
                vm.batchRename(paths, batchNames(paths.map { File(it) }, base))
                batchRenamePaths = null
            },
            onDismiss = { batchRenamePaths = null }
        )
    }
    nameDialog?.let { state ->
        NameDialog(
            state = state,
            check = { vm.checkName(activeUi.path, it, state.path) },
            onConfirm = { name ->
                when (state.kind) {
                    NameDialogKind.NEW_FOLDER -> vm.createFolder(activeIndex, name)
                    NameDialogKind.NEW_FILE -> vm.createFile(activeIndex, name)
                    NameDialogKind.RENAME -> vm.rename(activeIndex, state.path.orEmpty(), name)
                }
                nameDialog = null
            },
            onDismiss = { nameDialog = null }
        )
    }
    ncNameDialog?.let { state ->
        NameDialog(
            state = state,
            check = { vm.nc.checkName(it, state.path) },
            onConfirm = { name ->
                when (state.kind) {
                    NameDialogKind.RENAME -> vm.nc.rename(state.path.orEmpty(), name)
                    else -> vm.nc.makeFolder(name)
                }
                ncNameDialog = null
            },
            onDismiss = { ncNameDialog = null }
        )
    }
    serverNameDialog?.let { state ->
        NameDialog(
            state = state,
            check = { vm.servers.checkName(it, state.path) },
            onConfirm = { name ->
                when (state.kind) {
                    NameDialogKind.RENAME -> vm.servers.rename(state.path.orEmpty(), name)
                    else -> vm.servers.makeFolder(name)
                }
                serverNameDialog = null
            },
            onDismiss = { serverNameDialog = null }
        )
    }
    if (serverDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.server_items_delete_title),
            text = stringResource(R.string.server_items_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                vm.servers.deleteSelected()
                serverDeleteDialog = false
            },
            onDismiss = { serverDeleteDialog = false }
        )
    }
    if (ncDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.nc_delete_title),
            text = stringResource(R.string.nc_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                vm.nc.deleteSelected()
                ncDeleteDialog = false
            },
            onDismiss = { ncDeleteDialog = false }
        )
    }
    if (deleteDialog) {
        DeleteDialog(
            count = activeUi.selected.size,
            trashAvailable = !vm.isOnDrive(activeUi.path),
            onTrash = {
                vm.trashSelection(activeIndex)
                deleteDialog = false
            },
            onPermanent = {
                vm.deleteSelectionPermanently(activeIndex)
                deleteDialog = false
            },
            onDismiss = { deleteDialog = false }
        )
    }
    if (trashDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_permanently_title),
            text = stringResource(R.string.dialog_delete_permanently_text),
            confirmLabel = stringResource(R.string.action_delete_permanently),
            onConfirm = {
                if (remoteTrashShown) vm.nc.deleteSelectedTrash() else vm.deleteSelectedTrash()
                trashDeleteDialog = false
            },
            onDismiss = { trashDeleteDialog = false }
        )
    }
    if (emptyTrashDialog) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_empty_trash_title),
            text = stringResource(R.string.dialog_empty_trash_text),
            confirmLabel = stringResource(R.string.action_empty_trash),
            onConfirm = {
                if (remoteTrashShown) vm.nc.emptyTrash() else vm.emptyTrash()
                emptyTrashDialog = false
            },
            onDismiss = { emptyTrashDialog = false }
        )
    }
    passwordRequest?.let { request ->
        when (request) {
            is PasswordRequest.Extract -> PasswordDialog(
                title = stringResource(R.string.dialog_password_extract_title, File(request.zipPath).name),
                repeat = false,
                confirmLabel = stringResource(R.string.action_extract),
                onConfirm = { vm.answerPassword(it) },
                onDismiss = { vm.answerPassword(null) }
            )

            is PasswordRequest.Compress -> PasswordDialog(
                title = stringResource(R.string.dialog_password_compress_title),
                repeat = true,
                confirmLabel = stringResource(R.string.action_compress),
                onConfirm = { vm.answerPassword(it) },
                onDismiss = { vm.answerPassword(null) }
            )
        }
    }
    opState?.conflict?.let { conflict ->
        ConflictDialog(
            conflict = conflict,
            onAnswer = { resolution, all -> vm.answerConflict(resolution, all) },
            onCancelOperation = { vm.operations.cancelCurrent() }
        )
    }
    errorDialog?.let { ErrorsDialog(it) { errorDialog = null } }
}

@Composable
private fun ServerSelectionBar(vm: FilesViewModel, onDelete: () -> Unit, onRename: (String) -> Unit) {
    val state by vm.servers.remote.collectAsStateWithLifecycle()
    val single = state.items.filter { it.path in state.selected }.singleOrNull()
    ActionPill {
        PillButton(Icons.Rounded.Close, stringResource(R.string.common_cancel), { vm.servers.clearSelection() })
        PillButton(Icons.Rounded.Download, stringResource(R.string.action_download), { vm.servers.downloadSelected() })
        PillButton(Icons.Rounded.Delete, stringResource(R.string.action_delete), onDelete)
        if (single != null) {
            PillButton(Icons.Rounded.Edit, stringResource(R.string.action_rename), { onRename(single.path) })
        }
    }
}

@Composable
private fun NextcloudSelectionBar(vm: FilesViewModel, inPane: Boolean, onDelete: () -> Unit, onRename: (String) -> Unit) {
    val state by vm.nc.remote.collectAsStateWithLifecycle()
    val selectedItems = state.items.filter { it.path in state.selected }
    val single = selectedItems.singleOrNull()
    var moreOpen by remember { mutableStateOf(false) }
    ActionPill {
        PillButton(Icons.Rounded.Close, stringResource(R.string.common_cancel), { vm.nc.clearSelection() })
        if (inPane) {
            PillButton(Icons.Rounded.ContentCopy, stringResource(R.string.action_copy), { vm.copyRemoteSelection(false) })
            PillButton(Icons.Rounded.ContentCut, stringResource(R.string.action_cut), { vm.copyRemoteSelection(true) })
        }
        PillButton(Icons.Rounded.Download, stringResource(R.string.action_download), { vm.nc.downloadSelected() })
        PillButton(Icons.Rounded.Delete, stringResource(R.string.action_delete), onDelete)
        if (!inPane && single != null) {
            PillButton(Icons.Rounded.Edit, stringResource(R.string.action_rename), { onRename(single.path) })
        }
        if (inPane) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton(onClick = { moreOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more), tint = MaterialTheme.colorScheme.onSurface)
                }
                NooklinMenu(moreOpen, { moreOpen = false }) {
                    if (single != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            onClick = {
                                moreOpen = false
                                onRename(single.path)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy_to_other_pane)) },
                        onClick = {
                            moreOpen = false
                            vm.remoteToLocalPane(false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_to_other_pane)) },
                        onClick = {
                            moreOpen = false
                            vm.remoteToLocalPane(true)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilesSelectionBar(
    vm: FilesViewModel,
    activeIndex: Int,
    ui: PaneUi,
    dual: Boolean,
    shareEnabled: Boolean,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onProperties: (String) -> Unit,
    onBatchRename: (List<String>) -> Unit
) {
    val context = LocalContext.current
    var moreOpen by remember { mutableStateOf(false) }
    val selectedItems = ui.items.filter { it.path in ui.selected }
    val onlyFiles = selectedItems.isNotEmpty() && selectedItems.none { it.isDir }
    val single = selectedItems.singleOrNull()
    ActionPill {
        PillButton(Icons.Rounded.Close, stringResource(R.string.common_cancel), { vm.clearSelection(activeIndex) })
        PillButton(Icons.Rounded.ContentCopy, stringResource(R.string.action_copy), { vm.copySelection(activeIndex, false) })
        PillButton(Icons.Rounded.ContentCut, stringResource(R.string.action_cut), { vm.copySelection(activeIndex, true) })
        PillButton(Icons.Rounded.Delete, stringResource(R.string.action_delete), onDelete)
        if (shareEnabled && onlyFiles) {
            PillButton(Icons.Rounded.Share, stringResource(R.string.action_share), { shareFiles(context, selectedItems.map { it.path }) })
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onClick = { moreOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more), tint = MaterialTheme.colorScheme.onSurface)
            }
            NooklinMenu(moreOpen, { moreOpen = false }) {
                if (single != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = {
                            moreOpen = false
                            onRename(single.path)
                        }
                    )
                }
                if (single == null && selectedItems.size > 1) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = {
                            moreOpen = false
                            onBatchRename(selectedItems.map { it.path })
                        }
                    )
                }
                if (single != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_properties)) },
                        onClick = {
                            moreOpen = false
                            onProperties(single.path)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_duplicate)) },
                    onClick = {
                        moreOpen = false
                        vm.duplicate(activeIndex, selectedItems.map { it.path })
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_copy_path)) },
                    onClick = {
                        moreOpen = false
                        copyPathsToClipboard(context, selectedItems.map { it.path })
                        vm.clearSelection(activeIndex)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_compress)) },
                    onClick = {
                        moreOpen = false
                        vm.compressSelection(activeIndex)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_compress_password)) },
                    onClick = {
                        moreOpen = false
                        vm.compressSelectionWithPassword(activeIndex)
                    }
                )
                if (single != null && single.extension == "zip") {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_extract)) },
                        onClick = {
                            moreOpen = false
                            vm.extract(activeIndex, single.path)
                        }
                    )
                }
                if (dual) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy_to_other_pane)) },
                        onClick = {
                            moreOpen = false
                            vm.transferToOtherPane(activeIndex, false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_to_other_pane)) },
                        onClick = {
                            moreOpen = false
                            vm.transferToOtherPane(activeIndex, true)
                        }
                    )
                }
            }
        }
    }
}

private fun copyPathsToClipboard(context: android.content.Context, paths: List<String>) {
    val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    manager.setPrimaryClip(android.content.ClipData.newPlainText("path", paths.joinToString("\n")))
}
