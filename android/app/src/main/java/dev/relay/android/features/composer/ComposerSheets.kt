package dev.relay.android.features.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.relay.android.features.composer.logic.runtimeModes
import dev.relay.android.remote.model.ResumeSettings
import dev.relay.android.ui.ConfirmDialog
import dev.relay.android.ui.Group
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.RowSize
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Check

/** One choice in a [MenuSheet]. */
data class MenuOption(val label: String, val hint: String? = null, val checked: Boolean = false, val onClick: () -> Unit)

/** A sheet of choices; picking one runs it, then closes the sheet (an [onDismiss] that waits on a confirmation can keep it). */
@Composable
fun MenuSheet(title: String, options: List<MenuOption>, onDismiss: () -> Unit) {
    Sheet(onDismiss, title = title) {
        Group {
            options.forEach { o ->
                item {
                    ListRow(
                        o.label,
                        subtitle = o.hint,
                        trailing = if (o.checked) ({ Icon(TablerIcons.Outline.Check, "Chosen", tint = RelayTheme.colors.ink, size = 20.dp) }) else null,
                        size = if (o.hint == null) RowSize.Sm else RowSize.Default,
                        onClick = {
                            o.onClick()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/** Permissions, with full access confirmed first. */
@Composable
fun PermissionsSheet(settings: ResumeSettings, readOnly: Boolean, onSettings: (ResumeSettings) -> Unit, onDismiss: () -> Unit) {
    var confirm by remember { mutableStateOf<String?>(null) }
    MenuSheet(
        "Permissions",
        runtimeModes.filter { readOnly || it.value != "read-only" }.map { m ->
            MenuOption(m.label, m.description, m.value == settings.runtimeMode) {
                if (m.value == "full-access" && settings.runtimeMode != "full-access") confirm = m.value
                else onSettings(settings.copy(runtimeMode = m.value))
            }
        },
        onDismiss = { if (confirm == null) onDismiss() },
    )
    confirm?.let { mode ->
        ConfirmDialog(
            title = "Allow full access?",
            message = "The agent will run commands and edit files on your computer without asking.",
            confirmText = "Allow",
            onConfirm = {
                confirm = null
                onSettings(settings.copy(runtimeMode = mode))
                onDismiss()
            },
            onDismiss = {
                confirm = null
                onDismiss()
            },
        )
    }
}
