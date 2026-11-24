package dev.relay.android.features.composer

import androidx.compose.runtime.Composable
import dev.relay.android.features.composer.logic.ComposerChange
import dev.relay.android.features.composer.logic.sendLaterPresets
import dev.relay.android.features.composer.logic.wakeLabel
import dev.relay.android.features.composer.logic.withComposerChange
import dev.relay.android.remote.client.RemoteClient
import dev.relay.android.remote.model.ContextUsage
import dev.relay.android.remote.model.ResumeSettings
import dev.relay.android.remote.model.ThreadCost
import dev.relay.android.ui.Sheet

/** The composer's sheets; one is open at a time. */
internal enum class ComposerSheetKind { Model, Effort, Mode, Add, Later, Usage, Settings }

@Composable
internal fun ComposerSheetHost(
    open: ComposerSheetKind,
    /** Another sheet in its place, or null to close it. */
    onOpen: (ComposerSheetKind?) -> Unit,
    settings: ResumeSettings,
    onSettings: (ResumeSettings) -> Unit,
    modelLabel: String,
    lists: AgentLists,
    switched: (ResumeSettings, String) -> ResumeSettings,
    client: RemoteClient?,
    online: Boolean,
    projectId: String,
    readOnly: Boolean,
    attach: AttachActions,
    commands: AddCommands?,
    onLater: (at: Long) -> Unit,
    usage: UsageState,
    context: ContextUsage?,
    cost: ThreadCost?,
    onCompact: (suspend () -> Unit)?,
) {
    val provider = settings.provider
    val close = { onOpen(null) }
    val options = @Composable { model: String? ->
        ThreadOptions(
            settings, model,
            onModel = { onOpen(ComposerSheetKind.Model) },
            onPermissions = { onOpen(ComposerSheetKind.Mode) },
            onSettings = onSettings,
            usage = { UsageBar(provider, usage.usage, context, cost, onClick = { onOpen(ComposerSheetKind.Usage) }) },
        )
    }
    when (open) {
        ComposerSheetKind.Model, ComposerSheetKind.Effort -> ModelSheet(
            client, online, projectId, settings, lists.models, lists.offered,
            ModelSheetActions(
                settings = onSettings,
                agent = { onSettings(switched(settings, it)) },
                model = { to, model, known -> onSettings(withComposerChange(settings, ComposerChange.Model(to, model), known, switched)) },
                loadAll = { runCatching { lists.load(lists.offered) } },
            ),
            onDismiss = close,
            start = if (open == ComposerSheetKind.Effort) ModelSheetPage.Effort else ModelSheetPage.Models,
        )
        ComposerSheetKind.Mode -> PermissionsSheet(settings, readOnly, onSettings, close)
        ComposerSheetKind.Add -> AddSheet(attach, commands, close, options = { options(null) })
        ComposerSheetKind.Later -> {
            val now = System.currentTimeMillis()
            MenuSheet("Send later", sendLaterPresets(now).map { p -> MenuOption(p.label, wakeLabel(p.at, now)) { onLater(p.at) } }, close)
        }
        ComposerSheetKind.Usage -> UsageSheet(
            provider, usage.usage, context, cost,
            onDismiss = close,
            onRefresh = { usage.reload(true) },
            onCompact = onCompact,
            unavailable = usage.unavailable,
        )
        ComposerSheetKind.Settings -> Sheet(close, title = "Thread settings") { options(modelLabel) }
    }
}
