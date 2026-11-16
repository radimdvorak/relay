package dev.relay.android.features.thread

// The desktop's SecretRequestCard (src/features/secrets): an agent asked for
// a secret only the user has (request_secret). What is typed goes to the
// computer in one sealed bridge call and is kept nowhere on the phone.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.relay.android.features.thread.logic.secretDestination
import dev.relay.android.remote.model.SecretRequest
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Icon
import dev.relay.android.ui.SecretInput
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Key

/** [onAnswer] gets the typed secret, or null when the user declines. */
@Composable
internal fun SecretRequestCard(
    secret: SecretRequest,
    agent: String,
    busy: Boolean,
    error: String?,
    onAnswer: (String?) -> Unit,
) {
    // Not saveable: a typed secret isn't written into the app's saved state.
    var value by remember { mutableStateOf("") }
    val typed = value.trim()
    val c = RelayTheme.colors
    Column(
        Modifier.fillMaxWidth().border(1.dp, c.accent, RoundedCornerShape(14.dp)).background(c.card, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(TablerIcons.Outline.Key, null, size = 15.dp, tint = c.accent)
            Text(secret.name, Modifier.weight(1f), style = RelayTheme.type.base.copy(fontWeight = FontWeight.SemiBold))
        }
        secret.reason?.takeIf { it.isNotEmpty() }?.let { Hint(it) }
        SecretInput(
            value, { value = it },
            label = secret.name,
            placeholder = "Paste or type it…",
            enabled = !busy,
            onDone = { if (typed.isNotEmpty() && !busy) onAnswer(typed) },
        )
        Hint(secretDestination(agent))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Decline", { onAnswer(null) }, Modifier.weight(1f), variant = ButtonVariant.Outline, enabled = !busy)
            Button("Save", { onAnswer(typed) }, Modifier.weight(1f), enabled = !busy && typed.isNotEmpty())
        }
        error?.let { Hint(it, color = c.destructive) }
    }
}

/** How a secret request ended, said once. */
@Composable
internal fun SecretOutcomeRow(outcome: SecretOutcome) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(TablerIcons.Outline.Key, null, size = 13.dp, tint = RelayTheme.colors.mutedForeground)
            Hint(outcome.label)
        }
        outcome.note?.let { Hint(it, Modifier.padding(start = 18.dp)) }
    }
}
