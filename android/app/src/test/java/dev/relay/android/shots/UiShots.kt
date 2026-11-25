package dev.relay.android.shots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Badge
import dev.relay.android.ui.Banner
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Card
import dev.relay.android.ui.CardHeader
import dev.relay.android.ui.ListGroup
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.Tone
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Folder
import io.github.lyxnx.compose.ui.tablericons.outline.GitBranch
import org.junit.Test

/** The building blocks in `ui/`, on sample data. */
class UiShots : ShotTest() {
    @Test
    fun buttons() {
        for (dark in listOf(false, true)) Shot("buttons", dark = dark) {
            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (variant in ButtonVariant.entries) Button(variant.name, {}, variant = variant)
                Button("Loading", {}, loading = true)
                Button("Disabled", {}, enabled = false)
            }
        }
    }

    @Test
    fun blocks() {
        for (device in ShotDevice.entries) Shot("blocks", device) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Banner("Can't reach the computer", message = "Sample data: it was last seen 4min ago.", tone = Tone.Warning, actionLabel = "Retry", onAction = {})
                Card {
                    CardHeader("relay", description = "3 threads running")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge("Running", tone = Tone.Info)
                        Badge("Needs you", tone = Tone.Waiting)
                        Badge("Merged", tone = Tone.Success)
                    }
                }
                ListGroup {
                    ListRow("relay", subtitle = "~/code/relay", leadingIcon = TablerIcons.Outline.Folder, chevron = true, onClick = {})
                    ListRow("android-expressive", subtitle = "2 ahead of dev", leadingIcon = TablerIcons.Outline.GitBranch, chevron = true, onClick = {})
                }
            }
        }
    }
}
