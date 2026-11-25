package dev.relay.android.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.relay.android.features.home.HomeTab

/** What the shell keeps across screens: Home's tab, and whether Home is the pane beside the detail. */
@Stable
class ShellState(tab: MutableState<HomeTab>) {
    var tab by tab
    var sidePane by mutableStateOf(false)
}

@Composable
internal fun rememberShellState(sidePane: Boolean): ShellState {
    val tab = rememberSaveable { mutableStateOf(HomeTab.Threads) }
    val shell = remember(tab) { ShellState(tab) }
    shell.sidePane = sidePane
    return shell
}
