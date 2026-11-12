package dev.relay.android.features.composer

import androidx.compose.runtime.Composable
import dev.relay.android.features.composer.logic.agentName
import dev.relay.android.features.composer.logic.formatTokens
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Dialog

/**
 * The phone's take on the desktop's "Compact first" box by Send: a message
 * that finds a big conversation's cache cold asks whether to compact first.
 * Tapping outside sends nothing and keeps the draft.
 */
@Composable
internal fun CompactFirstDialog(
    provider: String,
    tokens: Long,
    onPick: (compact: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val size = formatTokens(tokens)
    Dialog(
        onDismiss,
        title = "Compact $size first?",
        description = "The cache went cold, so ${agentName(provider)} would read all $size tokens again. " +
            "Compacting first sums up the thread, then sends your message.",
        buttons = {
            Button("Send as is", { onPick(false) }, variant = ButtonVariant.Outline)
            Button("Compact first", { onPick(true) })
        },
    )
}
