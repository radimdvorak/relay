package dev.relay.android.features.thread

// What an answer leaves for the user besides its text: why it failed, a login
// to renew, pages it showed that only the computer can draw.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.relay.android.features.thread.logic.agentError
import dev.relay.android.features.thread.logic.agentName
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.AlertCircle
import io.github.lyxnx.compose.ui.tablericons.outline.Login

/** The desktop's AgentError: the provider's message out of its envelope, the envelope on request. */
@Composable
fun AgentErrorNote(error: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    val unfold = LocalUnfold.current
    val shown = agentError(error)
    val c = RelayTheme.colors
    Notice(TablerIcons.Outline.AlertCircle, c.danger) {
        SelectionContainer { Text(shown.message, style = RelayTheme.type.meta) }
        shown.hint?.let { Text(it, style = RelayTheme.type.meta, color = c.inkSoft) }
        val details = shown.details ?: return@Notice
        TextAction(if (open) "Hide technical details" else "Technical details", { unfold { open = !open } })
        if (open) SelectionContainer {
            Text(
                details.take(4000),
                Modifier.padding(top = 4.dp).fillMaxWidth().background(c.surface, TraceBox).padding(12.dp),
                style = RelayTheme.type.codeSmall,
                color = c.inkSoft,
            )
        }
    }
}

/** Something an answer has to say about itself: an icon in its colour, then words. */
@Composable
private fun Notice(icon: ImageVector, tint: Color, content: @Composable ColumnScope.() -> Unit) {
    QuietCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, size = 18.dp, tint = tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
        }
    }
}

/** The desktop offers to sign in again; that needs its terminal or browser, so the phone only says so. */
@Composable
fun SignInNote(provider: String) {
    val name = computerName(LocalThreadRemote.current)
    Notice(TablerIcons.Outline.Login, RelayTheme.colors.accent) {
        Text("${agentName(provider)} is signed out", style = RelayTheme.type.meta.copy(fontWeight = FontWeight.Medium), maxLines = 2)
        Text("Open this thread on $name to sign in again, then resume the answer.", style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft)
    }
}

