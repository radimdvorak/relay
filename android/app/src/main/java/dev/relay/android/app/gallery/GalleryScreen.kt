package dev.relay.android.app.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.ui.Page
import dev.relay.android.ui.navBarRoom
import dev.relay.android.ui.SegmentedTabs
import dev.relay.android.ui.TabOption
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayPaperDark
import dev.relay.android.ui.theme.RelayPaperLight
import dev.relay.android.ui.theme.RelayTheme

private enum class GalleryTheme { App, Light, Dark }

/** Every building block on sample data, in the app's theme or forced light/dark. */
@Composable
fun GalleryScreen() {
    val nav = LocalNavigator.current
    var theme by rememberSaveable { mutableStateOf(GalleryTheme.App) }
    var scrollChecks by rememberSaveable { mutableStateOf(false) }
    Page("Gallery", onBack = nav.backAction) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentedTabs(listOf(TabOption(false, "Components"), TabOption(true, "Scroll checks")), scrollChecks, { scrollChecks = it })
            SegmentedTabs(
                listOf(TabOption(GalleryTheme.App, "App"), TabOption(GalleryTheme.Light, "Light"), TabOption(GalleryTheme.Dark, "Dark")),
                theme,
                { theme = it },
            )
        }
        val body: @Composable () -> Unit = { if (scrollChecks) ScrollStabilityDemo() else Components() }
        when (theme) {
            GalleryTheme.App -> body()
            GalleryTheme.Light -> RelayTheme(RelayPaperLight, body)
            GalleryTheme.Dark -> RelayTheme(RelayPaperDark, body)
        }
    }
}

@Composable
private fun Components() {
    Column(
        Modifier.fillMaxSize().background(RelayTheme.colors.surface).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp + navBarRoom()),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("Everything here is sample data.", style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft)
        Foundations()
        ShellBlocks()
        Controls()
        Lists()
        Feedback()
        Overlays()
    }
}

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = RelayTheme.type.meta.copy(fontSize = 13.sp), color = RelayTheme.colors.inkFaint)
        content()
    }
}
