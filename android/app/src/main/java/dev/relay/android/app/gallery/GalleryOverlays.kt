package dev.relay.android.app.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.CommandGroup
import dev.relay.android.ui.CommandItem
import dev.relay.android.ui.CommandMenu
import dev.relay.android.ui.ConfirmDialog
import dev.relay.android.ui.Dialog
import dev.relay.android.ui.ListGroup
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.Switch
import dev.relay.android.ui.TextField
import dev.relay.android.ui.Tone
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Folder
import io.github.lyxnx.compose.ui.tablericons.outline.GitBranch

@Composable
internal fun Overlays() {
    val toasts = LocalToasts.current
    var sheet by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf(false) }
    Section("Overlays") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Sheet", { sheet = true }, variant = ButtonVariant.Outline)
            Button("Dialog", { dialog = true }, variant = ButtonVariant.Outline)
            Button("Confirm", { confirm = true }, variant = ButtonVariant.Outline)
            Button("Command menu", { command = true }, variant = ButtonVariant.Outline)
            Button("Toast", { toasts.show("Pushed to origin", description = "feature/kotlin · 3 commits", tone = Tone.Success) }, variant = ButtonVariant.Outline)
            Button("Undo toast", { toasts.undo("Thread archived") {} }, variant = ButtonVariant.Outline)
        }
    }
    if (sheet) Sheet({ sheet = false }, title = "Branch", description = "Switch the thread's worktree to another branch.") {
        ListGroup {
            listOf("main", "kotlin-phone", "feature/share").forEach { ListRow(it, leadingIcon = TablerIcons.Outline.GitBranch, onClick = { sheet = false }) }
        }
        Button("Create branch", { sheet = false }, Modifier.fillMaxWidth())
    }
    if (dialog) Dialog({ dialog = false }, "Rename thread", description = "Shown on the desktop too.", buttons = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Cancel", { dialog = false }, variant = ButtonVariant.Outline)
            Button("Save", { dialog = false })
        }
    }) {
        var name by remember { mutableStateOf("Kotlin phone app") }
        TextField(name, { name = it })
    }
    if (confirm) ConfirmDialog(
        "Discard changes?",
        onConfirm = { confirm = false },
        onDismiss = { confirm = false },
        message = "3 files go back to their last commit. This can't be undone.",
        confirmText = "Discard",
        destructive = true,
    )
    if (command) Sheet({ command = false }) {
        CommandMenu(
            rememberTextFieldState(),
            listOf(
                CommandGroup("Projects", listOf(CommandItem("relay", "relay", TablerIcons.Outline.Folder, hint = "12", checked = true), CommandItem("web", "website", TablerIcons.Outline.Folder, hint = "3"))),
                CommandGroup("Branches", listOf(CommandItem("main", "main", TablerIcons.Outline.GitBranch), CommandItem("k", "kotlin-phone", TablerIcons.Outline.GitBranch))),
            ),
            onSelect = { command = false },
            Modifier.fillMaxWidth().height(320.dp),
            framed = false,
        )
    }
}
