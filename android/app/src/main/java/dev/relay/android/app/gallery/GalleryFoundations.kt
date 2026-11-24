package dev.relay.android.app.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Text
import dev.relay.android.ui.ring
import dev.relay.android.ui.theme.RelayTheme

@Composable
internal fun Foundations() {
    val c = RelayTheme.colors
    Section("Colours") {
        val roles = listOf(
            "chrome" to c.chrome, "surface" to c.surface, "raised" to c.raised, "container" to c.container, "tool" to c.tool,
            "selected" to c.selected, "ink" to c.ink, "inkSoft" to c.inkSoft, "inkFaint" to c.inkFaint, "strong" to c.strong,
            "bubble" to c.bubble, "send" to c.send, "accent" to c.accent, "success" to c.success, "danger" to c.danger,
            "warning" to c.warning, "info" to c.info, "waiting" to c.waiting, "added" to c.added, "removed" to c.removed,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            roles.forEach { (name, color) -> Swatch(name, color) }
        }
    }
    Section("Type") {
        Text("What should we work on?", style = RelayTheme.type.greeting)
        Text("Title 24/30", style = RelayTheme.type.title)
        Text("Heading 20/26", style = RelayTheme.type.heading)
        Text("Row 16/22", style = RelayTheme.type.row)
        Text("Body 15/24: answers and messages read like this, with room between the lines.")
        Text("Meta 14/18", style = RelayTheme.type.meta, color = c.inkSoft)
        Text("Label 12/16", style = RelayTheme.type.label)
        Text("git rebase --onto dev", style = RelayTheme.type.code)
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(44.dp).background(color, RelayTheme.shapes.row).ring(RelayTheme.colors.line, RelayTheme.shapes.row))
        Text(name, style = RelayTheme.type.small, color = RelayTheme.colors.inkSoft, maxLines = 1)
    }
}
