package dev.relay.android.shots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.ui.Banner
import dev.relay.android.ui.BarTooltip
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Chip
import dev.relay.android.ui.CircleAction
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CirclePair
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.EffortSlider
import dev.relay.android.ui.Group
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.ListInset
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.RelaySheetFrame
import dev.relay.android.ui.RowSize
import dev.relay.android.ui.SearchField
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.SegmentedTabs
import dev.relay.android.ui.StatusToast
import dev.relay.android.ui.Switch
import dev.relay.android.ui.TabOption
import dev.relay.android.ui.Text
import dev.relay.android.ui.TextField
import dev.relay.android.ui.ToastState
import dev.relay.android.ui.Tone
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp
import io.github.lyxnx.compose.ui.tablericons.outline.Code
import io.github.lyxnx.compose.ui.tablericons.outline.Lock
import io.github.lyxnx.compose.ui.tablericons.outline.Message
import io.github.lyxnx.compose.ui.tablericons.outline.Pin
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import io.github.lyxnx.compose.ui.tablericons.outline.Search
import io.github.lyxnx.compose.ui.tablericons.outline.Settings
import io.github.lyxnx.compose.ui.tablericons.outline.X
import org.junit.Test

/** Every ui/ building block, and sample screens built from them, in both palettes. */
class UiKitShots : ShotTest() {
    private val palettes = listOf(false, true)

    @Test
    fun controls() {
        for (dark in palettes) Shot("kit-controls", dark = dark) { Controls() }
    }

    @Test
    fun lists() {
        for (dark in palettes) Shot("kit-lists", dark = dark) { Lists() }
    }

    @Test
    fun home() {
        for (device in ShotDevice.entries) for (dark in palettes) Shot("shell-home", device, dark) { SampleHome() }
    }

    @Test
    fun tooltip() {
        for (dark in palettes) Shot(
            "shell-tooltip",
            dark = dark,
            act = { compose.onNodeWithContentDescription("Projects").performClick() },
        ) { SampleHome() }
    }

    @Test
    fun composer() {
        for (dark in palettes) Shot("shell-composer", dark = dark) { SampleHome(expanded = true) }
    }

    @Test
    fun modelSheet() {
        for (device in ShotDevice.entries) for (dark in palettes) Shot("sheet-model", device, dark) {
            OverHome {
                RelaySheetFrame("Model", onClose = {}) {
                    Spacer(Modifier.height(36.dp))
                    SampleModelRow("Fable 5.2", selected = false)
                    SampleModelRow("Opus 5.5", selected = true)
                    SampleModelRow("Sonnet 5.5", selected = false)
                    SampleModelRow("Haiku 5.5", selected = false)
                    Text(
                        "For complex work and everyday tasks",
                        Modifier.padding(start = 38.dp, top = 20.dp, bottom = 160.dp),
                        style = RelayTheme.type.meta,
                        color = RelayTheme.colors.inkSoft,
                    )
                }
            }
        }
    }

    @Test
    fun effortSheet() {
        for (dark in palettes) for ((name, level) in listOf("medium" to 1, "max" to 4)) Shot("sheet-effort-$name", dark = dark) {
            OverHome { EffortSheet(level) }
        }
    }
}

@Composable
private fun EffortSheet(start: Int) {
    var level by remember(start) { mutableIntStateOf(start) }
    val labels = listOf("Low", "Medium", "High", "Extra", "Max")
    RelaySheetFrame("Effort", onClose = {}, back = true) {
        Text(
            labels[level],
            Modifier.fillMaxWidth().padding(top = 52.dp, bottom = 14.dp),
            style = RelayTheme.type.sheetTitle.copy(fontSize = 22.sp, lineHeight = 28.sp),
            textAlign = TextAlign.Center,
        )
        EffortSlider(level, { level = it }, Modifier.padding(horizontal = 20.dp), marked = 1, onPreview = { level = it })
        Text(
            "Higher effort means more thorough answers, but takes longer and uses your limits faster.",
            Modifier.padding(start = 38.dp, end = 38.dp, top = 120.dp, bottom = 80.dp),
            style = RelayTheme.type.body,
            color = RelayTheme.colors.inkSoft,
        )
    }
}

@Composable
private fun Controls() {
    var query by remember { mutableStateOf("relay") }
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(TablerIcons.Outline.Settings, "Settings", {})
            CircleButton(TablerIcons.Outline.Plus, "Add", {}, size = CircleSize.Composer)
            CircleButton(TablerIcons.Outline.ArrowUp, "Send", {}, size = CircleSize.Composer, tone = CircleTone.Strong)
            CircleButton(TablerIcons.Outline.X, "Close", {}, size = CircleSize.Small, tone = CircleTone.Clear)
            CirclePair(CircleAction(TablerIcons.Outline.Search, "Search", {}), CircleAction(TablerIcons.Outline.Plus, "New", {}), tone = CircleTone.Tool)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Opus 5.5", {})
            Chip("relay", {}, leadingIcon = TablerIcons.Outline.Code)
            BarTooltip("Projects")
        }
        SearchField(query, { query = it }, placeholder = "Search threads")
        SearchField("", {}, placeholder = "Search threads")
        TextField("", {}, label = "Branch name", placeholder = "feat/expressive")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Commit", {})
            Button("Push", {}, variant = ButtonVariant.Secondary)
            Button("Cancel", {}, variant = ButtonVariant.Outline)
            Button("Delete", {}, variant = ButtonVariant.Destructive)
        }
        SegmentedTabs(listOf(TabOption(0, "Changes"), TabOption(1, "Branches"), TabOption(2, "History")), tab, { tab = it })
        Banner("Can't reach this computer", message = "Retrying in 5s.", tone = Tone.Warning, actionLabel = "Retry", onAction = {})
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            StatusToast("Redesign the Android app", "Finished", icon = TablerIcons.Outline.Message)
        }
        EffortSlider(2, {}, marked = 1)
    }
}

@Composable
private fun Lists() {
    var on by remember { mutableStateOf(true) }
    CompositionLocalProvider(LocalToasts provides ToastState()) {
        Column(Modifier.fillMaxSize().padding(horizontal = ListInset, vertical = 8.dp)) {
            SectionLabel("Pinned")
            ListRow("Redesign the Android app", leadingIcon = TablerIcons.Outline.Message, meta = "12m", size = RowSize.Sm, selected = true)
            ListRow("Why does the composer jump?", leadingIcon = TablerIcons.Outline.Message, meta = "Nov 13", size = RowSize.Sm,
                trailing = { dev.relay.android.ui.Icon(TablerIcons.Outline.Pin, null, tint = RelayTheme.colors.inkSoft, size = 18.dp) })
            SectionLabel("Projects", count = "3")
            ListRow("relay", subtitle = "feat/sort-distance · +46 −31", meta = "2h")
            ListRow("website", subtitle = "main", meta = "Nov 13",
                trailing = { dev.relay.android.ui.Icon(TablerIcons.Outline.Lock, null, tint = RelayTheme.colors.inkSoft, size = 18.dp) })
            Spacer(Modifier.height(16.dp))
            Group(Modifier.padding(horizontal = 8.dp), label = "Appearance") {
                item { ListRow("Theme", subtitle = "Follow the computer", chevron = true, onClick = {}) }
                item { ListRow("Contrast", subtitle = "Default", chevron = true, onClick = {}) }
                item { ListRow("Frosted glass", trailing = { Switch(on, { on = it }) }) }
            }
            Spacer(Modifier.height(16.dp))
            Group(Modifier.padding(horizontal = 8.dp)) {
                item { ListRow("Open-source licences", chevron = true, onClick = {}, size = RowSize.Sm) }
            }
            Text(
                "Body text at 15/24: the answer reads like this, with a bold lead-in and a second line to show the leading.",
                Modifier.padding(16.dp),
            )
            Text("Heading 20/26", Modifier.padding(horizontal = 16.dp), style = RelayTheme.type.heading)
            Text("Small label · 12/16", Modifier.padding(16.dp), style = RelayTheme.type.label.copy(fontWeight = FontWeight.Medium), color = RelayTheme.colors.inkSoft)
        }
    }
}
