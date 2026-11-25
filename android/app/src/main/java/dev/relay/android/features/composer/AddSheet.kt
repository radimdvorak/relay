package dev.relay.android.features.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.relay.android.features.composer.logic.modeLabel
import dev.relay.android.remote.model.ResumeSettings
import dev.relay.android.ui.Group
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.Switch
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Camera
import io.github.lyxnx.compose.ui.tablericons.outline.Clipboard
import io.github.lyxnx.compose.ui.tablericons.outline.File
import io.github.lyxnx.compose.ui.tablericons.outline.ListCheck
import io.github.lyxnx.compose.ui.tablericons.outline.Photo
import io.github.lyxnx.compose.ui.tablericons.outline.ShieldCheck
import io.github.lyxnx.compose.ui.tablericons.outline.Slash
import io.github.lyxnx.compose.ui.tablericons.outline.Sparkles

/** Where an attachment comes from. */
internal class AttachActions(val library: () -> Unit, val camera: () -> Unit, val files: () -> Unit, val paste: () -> Unit)

/** What the sheet starts in the message: a command, or a skill in one already begun. */
internal class AddCommands(val label: String, val hint: String, val start: () -> Unit)

/** What's under the composer's plus: attachments in a row, then commands and how the thread runs. */
@Composable
internal fun AddSheet(
    attach: AttachActions,
    commands: AddCommands?,
    onDismiss: () -> Unit,
    options: @Composable () -> Unit,
) {
    Sheet(onDismiss, title = "Add") { AddContent(attach, commands, onDismiss, options) }
}

@Composable
internal fun AddContent(attach: AttachActions, commands: AddCommands?, onDismiss: () -> Unit, options: @Composable () -> Unit) {
    fun closing(action: () -> Unit) = {
        action()
        onDismiss()
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AttachTile(TablerIcons.Outline.Photo, "Photos", "Photo library", closing(attach.library))
        AttachTile(TablerIcons.Outline.Camera, "Camera", "Take a photo", closing(attach.camera))
        AttachTile(TablerIcons.Outline.File, "Files", "Files", closing(attach.files))
        AttachTile(TablerIcons.Outline.Clipboard, "Paste", "Paste image, the one you copied last", closing(attach.paste))
    }
    if (commands != null) Group {
        item { ListRow(commands.label, subtitle = commands.hint, leadingIcon = TablerIcons.Outline.Slash, onClick = closing(commands.start)) }
    }
    options()
}

@Composable
private fun RowScope.AttachTile(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    val c = RelayTheme.colors
    Column(
        Modifier
            .weight(1f)
            .pressable(onClick)
            .clip(RelayTheme.shapes.card)
            .background(c.container)
            .semantics { contentDescription = description }
            .heightIn(min = 80.dp)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = c.ink, size = 24.dp)
        Text(label, style = RelayTheme.type.meta, color = c.inkSoft, maxLines = 1)
    }
}

/** How the thread runs: permissions and plan mode, with the model too where no chip shows it ([model]). */
@Composable
internal fun ThreadOptions(
    settings: ResumeSettings,
    model: String?,
    onModel: () -> Unit,
    onPermissions: () -> Unit,
    onSettings: (ResumeSettings) -> Unit,
    usage: @Composable () -> Unit,
) {
    val plan = settings.interactionMode == "plan"
    fun setPlan(on: Boolean) = onSettings(settings.copy(interactionMode = if (on) "plan" else "default"))
    Group {
        if (model != null) item {
            ListRow("Model", subtitle = model, leadingIcon = TablerIcons.Outline.Sparkles, chevron = true, onClick = onModel)
        }
        item {
            ListRow(
                "Permissions", subtitle = modeLabel(settings.runtimeMode), leadingIcon = TablerIcons.Outline.ShieldCheck, chevron = true,
                onClick = onPermissions,
            )
        }
        item {
            ListRow(
                "Plan mode", subtitle = "Plans before it changes anything", leadingIcon = TablerIcons.Outline.ListCheck,
                trailing = { Switch(plan, { setPlan(it) }) },
                onClick = { setPlan(!plan) },
            )
        }
    }
    usage()
}
