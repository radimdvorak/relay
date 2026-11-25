package dev.relay.android.app.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Chip
import dev.relay.android.ui.CircleAction
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CirclePair
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.EffortSlider
import dev.relay.android.ui.IconButton
import dev.relay.android.ui.LineTabs
import dev.relay.android.ui.PairShapes
import dev.relay.android.ui.SearchField
import dev.relay.android.ui.SegmentedTabs
import dev.relay.android.ui.Switch
import dev.relay.android.ui.TabOption
import dev.relay.android.ui.Text
import dev.relay.android.ui.TextField
import dev.relay.android.ui.ValueSlider
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowRight
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import io.github.lyxnx.compose.ui.tablericons.outline.Search
import io.github.lyxnx.compose.ui.tablericons.outline.Trash
import io.github.lyxnx.compose.ui.tablericons.outline.X

@Composable
internal fun Controls() {
    Section("Buttons") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ButtonVariant.entries.forEach { Button(it.name, {}, variant = it) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            ButtonSize.entries.forEach { Button(it.name, {}, size = it, variant = ButtonVariant.Outline) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Button("New thread", {}, leadingIcon = TablerIcons.Outline.Plus)
            Button("Continue", {}, variant = ButtonVariant.Secondary, trailingIcon = TablerIcons.Outline.ArrowRight)
            Button("Saving", {}, loading = true)
            Button("Disabled", {}, enabled = false, variant = ButtonVariant.Outline)
            IconButton(TablerIcons.Outline.Search, "Search", {})
            IconButton(TablerIcons.Outline.Plus, "Add", {}, variant = ButtonVariant.Outline)
            IconButton(TablerIcons.Outline.Trash, "Delete", {}, variant = ButtonVariant.GhostDestructive)
        }
    }
    Section("Circles and chips") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(TablerIcons.Outline.Plus, "Add", {})
            CircleButton(TablerIcons.Outline.ArrowUp, "Send", {}, size = CircleSize.Composer, tone = CircleTone.Strong)
            CircleButton(TablerIcons.Outline.Plus, "Attach", {}, size = CircleSize.Composer)
            CircleButton(TablerIcons.Outline.X, "Close", {}, size = CircleSize.Small, tone = CircleTone.Clear)
            CirclePair(
                CircleAction(TablerIcons.Outline.Search, "Search", {}),
                CircleAction(TablerIcons.Outline.Plus, "New thread", {}),
                tone = CircleTone.Tool,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
            Chip("Opus 5.5", {}, shape = PairShapes.start)
            Chip("High", {}, shape = PairShapes.end)
        }
    }
    Section("Tabs") {
        var seg by remember { mutableStateOf("chat") }
        SegmentedTabs(listOf(TabOption("chat", "Chat"), TabOption("changes", "Changes"), TabOption("files", "Files")), seg, { seg = it })
        var line by remember { mutableStateOf(0) }
        LineTabs(listOf(TabOption(0, "Overview"), TabOption(1, "Commits"), TabOption(2, "Checks"), TabOption(3, "Files changed")), line, { line = it })
    }
    Section("Sliders") {
        var effort by remember { mutableIntStateOf(2) }
        var amount by remember { mutableIntStateOf(62) }
        EffortSlider(effort, { effort = it }, marked = 1, onPreview = { effort = it })
        ValueSlider(amount, { amount = it }, marked = 50)
    }
    Section("Fields") {
        var a by remember { mutableStateOf("") }
        var b by remember { mutableStateOf("feature/kotlin") }
        var d by remember { mutableStateOf("") }
        var q by remember { mutableStateOf("") }
        TextField(a, { a = it }, placeholder = "Thread title", label = "Title")
        TextField(b, { b = it }, label = "Branch", mono = true, error = if (b.contains(" ")) "No spaces in a branch name" else null)
        TextField(d, { d = it }, placeholder = "Describe the change", singleLine = false, minLines = 3, maxLength = 200)
        SearchField(q, { q = it })
        var on by remember { mutableStateOf(true) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(on, { on = it })
            Text("Notifications")
            Switch(false, {}, enabled = false)
        }
    }
}
