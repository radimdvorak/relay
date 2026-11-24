package dev.relay.android.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.relay.android.features.drawer.DrawerState
import dev.relay.android.features.drawer.rememberDrawerState
import dev.relay.android.features.home.HomeTab

/** What the shell keeps across screens: Home's tab, the drawer, and whether the side pane is showing. */
@Stable
class ShellState(tab: MutableState<HomeTab>, val drawer: DrawerState) {
    var tab by tab
    var sidePane by mutableStateOf(false)
}

@Composable
internal fun rememberShellState(sidePane: Boolean): ShellState {
    val tab = rememberSaveable { mutableStateOf(HomeTab.Threads) }
    val drawer = rememberDrawerState()
    val shell = remember(tab, drawer) { ShellState(tab, drawer) }
    shell.sidePane = sidePane
    return shell
}
