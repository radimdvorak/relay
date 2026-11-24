package dev.relay.android.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.BarTab
import dev.relay.android.ui.BottomBar
import dev.relay.android.ui.ChromeHeader
import dev.relay.android.ui.ChromeScreen
import dev.relay.android.ui.Chip
import dev.relay.android.ui.CircleAction
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CirclePair
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.ComposerPill
import dev.relay.android.ui.ComposerPlaceholder
import dev.relay.android.ui.EmptyGreeting
import dev.relay.android.ui.PairShapes
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.filled.Folders
import io.github.lyxnx.compose.ui.tablericons.filled.Messages
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronDown
import io.github.lyxnx.compose.ui.tablericons.outline.Edit
import io.github.lyxnx.compose.ui.tablericons.outline.Folders as FoldersOutline
import io.github.lyxnx.compose.ui.tablericons.outline.GitPullRequest
import io.github.lyxnx.compose.ui.tablericons.outline.Menu2
import io.github.lyxnx.compose.ui.tablericons.outline.Messages as MessagesOutline
import io.github.lyxnx.compose.ui.tablericons.outline.Microphone
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import io.github.lyxnx.compose.ui.tablericons.outline.Search

/*
 * Sample screens assembled from the ui/ building blocks, to compare against the reference frames.
 * They show the look only; the real shell and composer own behaviour.
 */

enum class SampleTab { Threads, Projects, Prs }

val sampleTabs = listOf(
    BarTab(SampleTab.Threads, TablerIcons.Outline.MessagesOutline, "Threads", TablerIcons.Filled.Messages),
    BarTab(SampleTab.Projects, TablerIcons.Outline.FoldersOutline, "Projects", TablerIcons.Filled.Folders, dot = true),
    BarTab(SampleTab.Prs, TablerIcons.Outline.GitPullRequest, "Pull requests"),
)

@Composable
fun SampleHeader() {
    ChromeHeader(
        leading = { CircleButton(TablerIcons.Outline.Menu2, "Menu", {}, tone = CircleTone.Raised) },
        trailing = {
            CirclePair(
                CircleAction(TablerIcons.Outline.Search, "Search", {}),
                CircleAction(TablerIcons.Outline.Edit, "New thread", {}),
            )
        },
    )
}

/** The Threads tab with nothing running: greeting, composer, bar. */
@Composable
fun SampleHome(expanded: Boolean = false, typed: String? = null) {
    var tab by remember { mutableStateOf(SampleTab.Threads) }
    ChromeScreen(header = { SampleHeader() }) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { EmptyGreeting() }
            SampleComposer(expanded, typed, Modifier.padding(horizontal = 15.dp))
            if (!expanded) BottomBar(sampleTabs, tab, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            else Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun SampleComposer(expanded: Boolean, typed: String?, modifier: Modifier = Modifier) {
    ComposerPill(
        field = {
            if (typed == null) ComposerPlaceholder("Message")
            else Text(typed, style = RelayTheme.type.row)
        },
        modifier = modifier.fillMaxWidth(),
        expanded = expanded,
        leading = { CircleButton(TablerIcons.Outline.Plus, "Add", {}, size = CircleSize.Composer) },
        chips = {
            Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                Chip("Opus 5.5", {}, shape = PairShapes.start)
                Chip("High", {}, trailingIcon = TablerIcons.Outline.ChevronDown, shape = PairShapes.end)
            }
        },
        trailing = {
            CircleButton(TablerIcons.Outline.Microphone, "Dictate", {}, size = CircleSize.Composer)
            CircleButton(TablerIcons.Outline.ArrowUp, "Send", {}, size = CircleSize.Composer, tone = CircleTone.Strong)
        },
    )
}

/** A sheet over the home, behind its scrim, inset like the real one. */
@Composable
fun OverHome(sheet: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        SampleHome()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (RelayTheme.colors.isDark) 0.5f else 0.16f)))
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp), content = sheet)
    }
}

/** The model sheet's selected row: name, a tiny effort glyph, then the effort and a chevron. */
@Composable
fun SampleModelRow(name: String, selected: Boolean) {
    val c = RelayTheme.colors
    val style = RelayTheme.type.row.copy(fontWeight = FontWeight.Medium)
    Row(
        Modifier.fillMaxWidth().height(if (selected) 64.dp else 57.dp).padding(horizontal = 16.dp)
            .then(if (selected) Modifier.background(c.tool, RelayTheme.shapes.circle) else Modifier)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, Modifier.weight(1f), style = style, color = if (selected) c.ink else c.inkSoft)
        if (selected) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 5) Box(
                    Modifier.size(if (i == 1) 2.dp else 3.dp, if (i == 1) 10.dp else 3.dp)
                        .background(if (i == 1) c.ink else c.inkFaint, RelayTheme.shapes.circle),
                )
            }
            Spacer(Modifier.weight(0.6f))
            Text("Medium  ›", style = RelayTheme.type.meta, color = c.inkSoft)
        }
    }
}
