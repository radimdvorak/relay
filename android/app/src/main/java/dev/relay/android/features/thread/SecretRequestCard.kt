package dev.relay.android.features.thread

// The desktop's SecretRequestCard (src/features/secrets): an agent asked for
// a secret only the user has (request_secret). What is typed goes to the
// computer in one sealed bridge call and is kept nowhere on the phone.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.relay.android.features.thread.logic.SecretOutcome
import dev.relay.android.features.thread.logic.alwaysExplained
import dev.relay.android.features.thread.logic.secretDestination
import dev.relay.android.remote.model.SavedSecrets
import dev.relay.android.remote.model.SecretRequest
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Icon
import dev.relay.android.ui.SecretInput
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Key

/**
 * [onAnswer] gets the typed secret, or null when the user declines, and whether
 * to remember it for the project. [saved] is what the project already has
 * saved; null where the computer is too old to offer Always.
 */
@Composable
internal fun SecretRequestCard(
    secret: SecretRequest,
    agent: String,
    busy: Boolean,
    error: String?,
    saved: SavedSecrets?,
    onAnswer: (String?, Boolean) -> Unit,
) {
    // Not saveable: a typed secret isn't written into the app's saved state.
    var value by remember { mutableStateOf("") }
    val typed = value.trim()
    val c = RelayTheme.colors
    QuietCard(padding = 16.dp, gap = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TraceGap)) {
            Icon(TablerIcons.Outline.Key, null, size = 18.dp, tint = c.waiting)
            Text(secret.name, Modifier.weight(1f), style = RelayTheme.type.body.copy(fontWeight = FontWeight.SemiBold))
        }
        secret.reason?.takeIf { it.isNotEmpty() }?.let { Hint(it) }
        SecretInput(
            value, { value = it },
            label = secret.name,
            placeholder = "Paste or type it…",
            enabled = !busy,
            onDone = { if (typed.isNotEmpty() && !busy) onAnswer(typed, false) },
            fill = c.surface,
        )
        Hint(secretDestination(agent, saved?.computer))
        if (saved != null) {
            Hint(alwaysExplained(secret.name, saved.secrets.map { it.name }, saved.sessionOnly == true, saved.computer))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Decline", { onAnswer(null, false) }, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = !busy)
            if (saved != null) Button(
                "Always", { onAnswer(typed, true) }, Modifier.weight(1f),
                variant = ButtonVariant.Secondary, enabled = !busy && typed.isNotEmpty(),
            )
            Button("Save", { onAnswer(typed, false) }, Modifier.weight(1f), enabled = !busy && typed.isNotEmpty())
        }
        error?.let { Hint(it, color = c.danger) }
    }
}

/** How a secret request ended, said once. */
@Composable
internal fun SecretOutcomeRow(outcome: SecretOutcome) {
    Column {
        Step {
            StepIcon(TablerIcons.Outline.Key)
            StepText(outcome.label)
        }
        outcome.note?.let { Hint(it, Modifier.padding(start = TraceIndent)) }
    }
}
