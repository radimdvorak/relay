package dev.relay.android.features.composer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.CenteredCard
import dev.relay.android.ui.InsetModalSheet
import dev.relay.android.ui.RelaySheetFrame
import dev.relay.android.ui.rememberIsWide

/**
 * A sheet whose body does its own scrolling (the model wheel, the effort slider), unlike ui's
 * `Sheet`, which scrolls what it's given. On a wide window it's a centred card, like the rest.
 * With [onBack] its ✕ is a ←, and the back gesture goes there instead of closing it.
 */
@Composable
internal fun ComposerSheet(
    title: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val wide = rememberIsWide()
    val frame = @Composable {
        BackHandler(enabled = onBack != null) { onBack?.invoke() }
        RelaySheetFrame(title, onBack ?: onDismiss, back = onBack != null, handle = !wide, action = action, content = content)
    }
    if (wide) CenteredCard(onDismiss, Modifier.widthIn(max = 480.dp)) { frame() }
    else InsetModalSheet(onDismiss, halfFirst = false) { frame() }
}
