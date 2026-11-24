package dev.relay.android.app.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Badge
import dev.relay.android.ui.Banner
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.Card
import dev.relay.android.ui.EmptyState
import dev.relay.android.ui.ProgressBar
import dev.relay.android.ui.Skeleton
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.StatusDot
import dev.relay.android.ui.StatusToast
import dev.relay.android.ui.Tone
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Folder
import io.github.lyxnx.compose.ui.tablericons.outline.GitBranch
import io.github.lyxnx.compose.ui.tablericons.outline.MessageCircle

@Composable
internal fun Feedback() {
    Section("Statuses") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Tone.entries.forEach { Badge(it.name, tone = it) }
            Badge("main", icon = TablerIcons.Outline.GitBranch)
            StatusDot(Tone.Success)
            StatusDot(Tone.Waiting)
        }
    }
    Section("Banners") {
        Banner("Computer asleep", message = "It will catch up when it wakes.", tone = Tone.Info)
        Banner("Weekly limit at 92%", tone = Tone.Warning, actionLabel = "Usage", onAction = {})
        Banner("Couldn't push", message = "Rejected: the remote has newer commits.", tone = Tone.Destructive, onDismiss = {})
    }
    Section("Status toast") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            StatusToast("Redesign the Android app", "Finished", icon = TablerIcons.Outline.MessageCircle)
        }
    }
    Section("Loading") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spinner()
            ProgressBar(0.4f, Modifier.weight(1f))
            ProgressBar(null, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Skeleton(Modifier.width(180.dp).height(12.dp))
                Skeleton(Modifier.width(120.dp).height(12.dp))
            }
        }
    }
    Section("Empty state") {
        Card(padding = 0.dp) { EmptyState("No threads yet", description = "Start one from your computer or here.", icon = TablerIcons.Outline.Folder, fan = true, action = { Button("New thread", {}, size = ButtonSize.Sm) }) }
    }
}
