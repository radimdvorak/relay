package dev.relay.android.app.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Avatar
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Card
import dev.relay.android.ui.CardHeader
import dev.relay.android.ui.CardVariant
import dev.relay.android.ui.Group
import dev.relay.android.ui.ListGroup
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.ProjectBadge
import dev.relay.android.ui.StatusDot
import dev.relay.android.ui.Switch
import dev.relay.android.ui.Text
import dev.relay.android.ui.Tone
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.GitCommit
import io.github.lyxnx.compose.ui.tablericons.outline.MessageCircle

@Composable
internal fun Lists() {
    Section("Rows") {
        var picked by remember { mutableStateOf(1) }
        ListGroup {
            ListRow("Fix the pairing retry", subtitle = "relay · 2 min ago", leading = { ProjectBadge("relay", size = 40.dp) }, onClick = { picked = 0 }, selected = picked == 0)
            ListRow("Kotlin phone app", subtitle = "Writing RemoteClient.kt", leadingIcon = TablerIcons.Outline.MessageCircle, trailing = { StatusDot(Tone.Info) }, onClick = { picked = 1 }, selected = picked == 1)
            ListRow("Commit 2ff00d01", subtitle = "Report a problem from Settings", leadingIcon = TablerIcons.Outline.GitCommit, chevron = true, onClick = { picked = 2 }, selected = picked == 2)
        }
    }
    Section("Group") {
        var on by remember { mutableStateOf(true) }
        Group(label = "Appearance") {
            item { ListRow("Theme", subtitle = "Follow the computer", chevron = true, onClick = {}) }
            item { ListRow("Frosted glass", trailing = { Switch(on, { on = it }) }, onClick = { on = !on }) }
            item { ListRow("Open-source licences", chevron = true, onClick = {}) }
        }
    }
    Section("Cards") {
        Card {
            CardHeader("relay", description = "3 threads running", action = { Button("Open", {}, variant = ButtonVariant.Outline, size = ButtonSize.Sm) })
            Text("A quiet tonal fill, no outline or shadow.", color = RelayTheme.colors.inkSoft)
        }
        Card(Modifier.fillMaxWidth(), variant = CardVariant.Muted, padding = 16.dp) { Text("Muted card") }
    }
    Section("Avatars") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar("Jane Doe", size = 24.dp)
            Avatar("Jane Doe")
            Avatar("relay", size = 40.dp, rounded = true)
            listOf("relay", "website", "kotlin-phone", "fframes").forEach { ProjectBadge(it, size = 24.dp) }
        }
    }
}
