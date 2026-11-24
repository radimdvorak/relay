package dev.relay.android.app.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.BarGlyph
import dev.relay.android.ui.BarTab
import dev.relay.android.ui.BarTooltip
import dev.relay.android.ui.BottomBar
import dev.relay.android.ui.ChromeHeader
import dev.relay.android.ui.ChromeScreen
import dev.relay.android.ui.CircleAction
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CirclePair
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.ComposerPill
import dev.relay.android.ui.ComposerPlaceholder
import dev.relay.android.ui.EmptyGreeting
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.RelaySheetFrame
import dev.relay.android.ui.RowSize
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp
import io.github.lyxnx.compose.ui.tablericons.outline.Edit
import io.github.lyxnx.compose.ui.tablericons.outline.Menu2
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import io.github.lyxnx.compose.ui.tablericons.outline.Search

/** A small phone put together from the shell's pieces, each of them live. */
@Composable
internal fun ShellBlocks() {
    Section("Shell") {
        var tab by remember { mutableIntStateOf(0) }
        Box(Modifier.fillMaxWidth().height(440.dp).clip(RelayTheme.shapes.card)) {
            ChromeScreen(
                header = {
                    ChromeHeader(
                        title = "relay",
                        leading = { CircleButton(TablerIcons.Outline.Menu2, "Menu", {}, tone = CircleTone.Raised) },
                        trailing = { CirclePair(CircleAction(TablerIcons.Outline.Search, "Search", {}), CircleAction(TablerIcons.Outline.Edit, "New thread", {})) },
                    )
                },
            ) {
                EmptyGreeting(Modifier.align(Alignment.Center))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComposerPill(
                        field = { ComposerPlaceholder("Message") },
                        leading = { CircleButton(TablerIcons.Outline.Plus, "Attach", {}, size = CircleSize.Composer, tone = CircleTone.Clear) },
                        trailing = { CircleButton(TablerIcons.Outline.ArrowUp, "Send", {}, size = CircleSize.Composer, tone = CircleTone.Strong) },
                    )
                    BottomBar(
                        listOf(
                            BarTab(0, BarGlyph.Threads, "Threads"),
                            BarTab(1, BarGlyph.Projects, "Projects", dot = true),
                            BarTab(2, BarGlyph.Pulls, "PRs", "Pull requests"),
                        ),
                        tab,
                        { tab = it },
                    )
                }
            }
        }
    }
    Section("Tooltip") { BarTooltip("Projects") }
    Section("Sheet frame") {
        RelaySheetFrame("Model", onClose = {}, Modifier.background(RelayTheme.colors.chrome).padding(8.dp)) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp)) {
                ListRow("Fable 5.2", size = RowSize.Sm, onClick = {})
                ListRow("Opus 5.5", size = RowSize.Sm, selected = true, onClick = {})
            }
        }
    }
}
