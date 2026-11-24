package dev.relay.android.app

import android.app.Activity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import dev.relay.android.ui.LocalSharedTransitions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.relay.android.features.drawer.DrawerOverlay
import dev.relay.android.features.drawer.drawerEdgeSwipe
import dev.relay.android.lib.navigation.LocalOpenDrawer
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.relay.android.features.applock.LockOverlay
import dev.relay.android.features.home.ConnectionLine
import dev.relay.android.features.home.StatusToasts
import dev.relay.android.features.markdown.LocalOpensLocalhost
import dev.relay.android.features.tunnel.rememberTunnelLinks
import dev.relay.android.features.tunnel.rememberTunnels
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.remote.model.PhoneAppearance
import dev.relay.android.remote.model.PhonePalette
import dev.relay.android.remote.model.RemoteJson
import dev.relay.android.ui.LocalScreenNotice
import dev.relay.android.ui.theme.DesktopAppearance
import dev.relay.android.lib.navigation.DeepLink
import dev.relay.android.lib.navigation.Route
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.ToastHost
import dev.relay.android.ui.ToastState
import dev.relay.android.ui.rememberIsWide
import dev.relay.android.ui.theme.RelayTheme
import dev.relay.android.ui.theme.resolveColors
import dev.relay.android.ui.theme.withContrast
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun PhonePalette.toMap(): Map<String, String> =
    RemoteJson.encodeToJsonElement(PhonePalette.serializer(), this).jsonObject.mapValues { it.value.jsonPrimitive.content }

private fun PhoneAppearance.toDesktop() = DesktopAppearance(mode, light.toMap(), dark.toMap())

/** Whether this process has looked at the back stack it started with. */
private var stackRestored = false

/**
 * The whole app: theme, navigator, toasts, and either one stack (phone) or
 * Home in a sidebar beside a detail pane (600dp and wider). After a [crash],
 * only its page until [onCrashSeen].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RelayRoot(container: AppContainer, links: ReceiveChannel<DeepLink>, crash: Crash?, onCrashSeen: () -> Unit) {
    val prefs = container.prefs
    val remote = container.remote
    val systemDark = isSystemInDarkTheme()
    val overview by remote.overview.collectAsState()
    val ready by remote.ready.collectAsState()
    val client by remote.client.collectAsState()
    val paired = client != null
    // The desktop's palette, kept in the offline overview so it's there before it connects.
    val appearance = overview?.appearance
    val colors = remember(prefs.themeMode, prefs.contrast, systemDark, appearance) {
        resolveColors(prefs.themeMode, appearance?.toDesktop(), systemDark).withContrast(prefs.contrast)
    }
    RelayTheme(colors) {
        val backStack = rememberNavBackStack(Route.Home)
        // A window over the rest, so it's here whatever else is: a crash's page, open sheets.
        LockOverlay(container.lock) {
            // Forgot PIN: every computer goes, as the lock did.
            remote.forgetAll()
            // The screens open under the lock go too, and their models with them: a file or a diff
            // they had read would show again past a PIN nobody typed.
            backStack.clear()
            backStack.add(Route.Home)
        }
        if (crash != null) {
            SystemBars(dark = colors.isDark)
            return@RelayTheme CrashScreen(crash, onCrashSeen)
        }
        val top = backStack.lastOrNull() as? Route
        // Pairing is always dark, whatever the theme.
        SystemBars(dark = colors.isDark || top is Route.Pair)
        // Panes only once there's a computer to list; pairing is one whole screen.
        val panes = rememberIsWide() && ready && paired && top !is Route.Pair
        val isWide by rememberUpdatedState(panes)
        val navigator = remember(backStack) { AppNavigator(backStack) { isWide } }
        val toasts = remember { ToastState() }
        val shell = rememberShellState(panes && !prefs.sidebarHidden)
        val sidebar = panes && !prefs.sidebarHidden
        val activity = LocalView.current.context as? Activity

        // A new process (an update, or Android reclaiming the app) gets back the stack the old one
        // saved, pairing screen and all. Nobody means to wake up there: it goes, and comes back
        // below if the phone really has no computer. A fold or unfold keeps the process, and a
        // pairing in progress with it.
        LaunchedEffect(backStack) {
            if (stackRestored) return@LaunchedEffect
            stackRestored = true
            backStack.removeAll { it is Route.Pair }
        }
        // A link (a notification's tap) lands on a screen of its own: the keyboard the last one had
        // up would stay and squeeze it.
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        LaunchedEffect(navigator) {
            for (link in links) {
                val route = link.route
                shell.drawer.close()
                // A notification posted before a switch: its thread is on the computer it names.
                if (link.computer != null) {
                    remote.ready.first { it }
                    val leaving = remote.active.value != link.computer
                    if (!remote.switchTo(link.computer)) continue
                    // Whatever is open belongs to the computer being left.
                    if (leaving) navigator.home()
                }
                if (navigator.stack.lastOrNull() != route) {
                    focus.clearFocus()
                    keyboard?.hide()
                }
                navigator.open(route)
            }
        }
        // Decided here, from one composition's reads: inside the effect, `ready` would be read live
        // while `paired` stayed as composed, and a launch that drew before the saved computer was
        // read saw it ready and unpaired at once, and opened pairing over a paired phone.
        val unpaired = ready && !paired && top == Route.Home
        LaunchedEffect(unpaired) {
            if (unpaired) navigator.push(Route.Pair())
        }
        // Home and search say it themselves, as do Settings and pairing.
        val notice: (@Composable () -> Unit)? =
            if (top is Route.Home || top is Route.Search || top is Route.Settings || top is Route.Pair) null
            else ({ ConnectionLine(remote) })
        val openDrawer = { shell.drawer.open() }.takeIf { paired && !panes && top !is Route.Pair }
        // Unfolded with the drawer out: the pane takes over.
        LaunchedEffect(panes) { if (panes) shell.drawer.close() }

        val tunnelLinks = rememberTunnelLinks(remote, LocalUriHandler.current, navigator)
        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalToasts provides toasts,
            LocalUriHandler provides tunnelLinks,
            LocalOpensLocalhost provides rememberTunnels(remote),
            LocalOpenDrawer provides openDrawer,
        ) {
            Box(Modifier.fillMaxSize().background(RelayTheme.colors.chrome)) {
                val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
                WideLayout(
                    panes, prefs,
                    sidebar = { SidePane(container, shell, navigator) },
                    modifier = Modifier
                        .drawerEdgeSwipe(shell.drawer, enabled = openDrawer != null && top == Route.Home, screenWidth = windowWidth)
                        .then(if (shell.drawer.visible) Modifier.clearAndSetSemantics { } else Modifier),
                ) {
                    SharedTransitionLayout {
                        CompositionLocalProvider(LocalScreenNotice provides notice, LocalSharedTransitions provides this.takeIf { !panes }) {
                            NavDisplay(
                                backStack = backStack,
                                // Nothing paired: Home behind pairing is only a redirect, so back leaves the app.
                                onBack = { if (!paired && top is Route.Pair && backStack.size == 2) activity?.finish() else navigator.pop() },
                                entryDecorators = listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                ),
                                transitionSpec = { forward(targetState, navigator.swapped) },
                                popTransitionSpec = { back(initialState) },
                                predictivePopTransitionSpec = { back(initialState) },
                                entryProvider = entryProvider { appEntries(container, shell) },
                            )
                        }
                    }
                }
                StatusToasts(
                    remote,
                    openId = (top as? Route.Thread)?.id,
                    hidden = top is Route.Pair,
                    pane = panes,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = if (top.hasComposer) COMPOSER_ROOM else 0.dp),
                )
                if (!panes) DrawerOverlay(shell.drawer) { PhoneDrawer(container, shell, navigator) }
                ToastHost(toasts, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
            }
        }
    }
}

/** What a composer at the screen's bottom takes, for what floats over the screen. */
private val COMPOSER_ROOM = 100.dp

private val Route?.hasComposer get() = this is Route.Thread || this is Route.Reply || this is Route.NewThread

@Composable
private fun SystemBars(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        // Three-button navigation sits on the screen's own colour, not on a scrim of the system's.
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
