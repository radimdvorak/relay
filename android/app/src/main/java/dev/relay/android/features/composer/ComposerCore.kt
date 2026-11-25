package dev.relay.android.features.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.relay.android.features.composer.logic.CommandResult
import dev.relay.android.features.composer.logic.CommandSettings
import dev.relay.android.features.composer.logic.ComposeDraft
import dev.relay.android.features.composer.logic.DraftCommand
import dev.relay.android.features.composer.logic.FilePhase
import dev.relay.android.features.composer.logic.NewThreadModels
import dev.relay.android.features.composer.logic.agentInfo
import dev.relay.android.features.composer.logic.agentName
import dev.relay.android.features.composer.logic.agentProviders
import dev.relay.android.features.composer.logic.appendQuote
import dev.relay.android.features.composer.logic.composeAttempt
import dev.relay.android.features.composer.logic.composerCommand
import dev.relay.android.features.composer.logic.draftCommand
import dev.relay.android.features.composer.logic.isComposerCommand
import dev.relay.android.features.composer.logic.modelName
import dev.relay.android.features.composer.logic.offersEffort
import dev.relay.android.features.composer.logic.sameDraft
import dev.relay.android.features.composer.logic.shortEffortLabel
import dev.relay.android.features.composer.logic.withComposerChange
import dev.relay.android.features.diagnostics.LocalScrollTrace
import dev.relay.android.features.dictation.DictationButton
import dev.relay.android.features.dictation.DictationInput
import dev.relay.android.features.dictation.canDictate
import dev.relay.android.features.renders.composeInto
import dev.relay.android.remote.Bridge
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.LinkState
import dev.relay.android.remote.drafts.Sends
import dev.relay.android.remote.drafts.hasExtras
import dev.relay.android.remote.model.ContextUsage
import dev.relay.android.remote.model.ResumeSettings
import dev.relay.android.remote.model.ThreadCost
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// The desktop's composer on a phone (mobile/src/ui/Composer.tsx): a pill that grows into a card
// with the model and effort under the message, the rest behind its plus.

private val wantsCommandsStart = Regex("^\\s*[/$]")
private val wantsCommandsSkill = Regex("\\s\\$\\S*$")
private val skillTail = Regex("(^|\\s)[$/][^\\s]*$")

/** Under the box, down to the keyboard or the screen's end. */
internal val COMPOSER_BOTTOM = 10.dp

@Composable
internal fun ComposerCore(
    remote: Remote,
    projectId: String,
    settings: ResumeSettings,
    onSettings: (ResumeSettings) -> Unit,
    running: Boolean,
    /** Resolves false when the message wasn't sent, to keep the draft; throws to show why. */
    onSend: suspend (Composed) -> Boolean,
    modifier: Modifier = Modifier,
    handle: ComposerHandle? = null,
    placeholder: String? = null,
    disabled: Boolean = false,
    context: ContextUsage? = null,
    cost: ThreadCost? = null,
    onStop: (() -> Unit)? = null,
    /** Relay's workspace commands: /changes, /files, /new, /clear, /compact. */
    onCommand: (suspend (name: String, args: String) -> RunResult)? = null,
    /** Where unsent text is kept between visits: the thread, or the reply's root. */
    draftKey: String? = null,
    /** A new thread's destination, including its workspace, for retrying a send. */
    sendTarget: String? = null,
    /** A new thread's models, per agent, for switching to one not picked here yet. */
    remembered: NewThreadModels = emptyMap(),
    autoFocus: Boolean = false,
    /** Stays the pill even while it has focus, until what it waits for lets it grow. */
    collapsed: Boolean = false,
    /** On the message box itself, not the dock around it. */
    boxModifier: Modifier = Modifier,
    /** Draws Send as the New thread button (pencil, its fill), with this modifier on it. */
    newThreadSend: Modifier? = null,
    /** The screen shows the plan's limits (through [handle]), so they aren't repeated at the end of [above]. */
    settingsUp: Boolean = false,
    /** Over the message box, inside the dock. */
    above: @Composable ColumnScope.() -> Unit = {},
) {
    val c = RelayTheme.colors
    val appContext = LocalContext.current
    val drafts = remember { Sends.drafts(appContext.filesDir) }
    remember(remote) { Sends.attach(remote, appContext.filesDir) }
    val scope = rememberCoroutineScope()
    val computer by remote.active.collectAsState()
    val status by remote.status.collectAsState()
    val client by remote.client.collectAsState()
    val overview by remote.overview.collectAsState()
    val connection by Sends.connection.collectAsState()
    drafts.version.collectAsState().value // Recompose as the shared draft changes.
    val online = status.state == LinkState.Online

    val field = rememberTextFieldState()
    var value by field.asValue()
    val text = value.text
    fun setText(next: String) {
        if (next != value.text) value = TextFieldValue(next, TextRange(next.length))
    }
    val focus = remember { FocusRequester() }
    var typing by remember { mutableStateOf(false) }
    val unsent = rememberUnsent(computer, draftKey)
    var images by unsent.images
    var files by unsent.files
    val picked by FileUploads.files.collectAsState()
    // A file whose copy couldn't be made leaves the draft.
    LaunchedEffect(picked) {
        val live = files.filter { it in picked }
        if (live.size != files.size) files = live
    }
    var error by unsent.error
    var failedSend by unsent.failedSend
    var busy by remember { mutableStateOf(false) }
    var drawingOn by remember { mutableStateOf<Attachment?>(null) }
    var sheet by remember { mutableStateOf<ComposerSheetKind?>(null) }
    var dismissed by remember { mutableStateOf<String?>(null) }
    val provider = settings.provider

    val dictation = rememberComposerDictation(remote, draftKey)
    val input = remember { DictationInput({ value }, { value = it }, { typing }) }

    FollowDraft(drafts, computer, draftKey, { value.text }, ::setText)
    val shared = computer?.let { pc -> draftKey?.let { drafts.shared(pc, it) } }
    // Screenshots the computer's composer has in this draft; they go with it from there.
    val computerShots = shared?.extras?.screenshots.orEmpty()

    val switched = rememberAgentSwitch(settings, remembered)

    val picking = sheet == ComposerSheetKind.Model || sheet == ComposerSheetKind.Effort
    val lists = rememberAgentLists(client, computer, connection, online, provider, picking, appContext)
    val catalogs = lists.models
    val modelLabel = if (settings.choice.model.isNotEmpty()) modelName(provider, catalogs[provider], settings.choice.model) else "Default"

    val usage = rememberUsage(remote, provider)
    val wantsCommands = wantsCommandsStart.containsMatchIn(text) || wantsCommandsSkill.containsMatchIn(text)
    val commands = rememberProviderCommands(client, online, connection, projectId, provider, wantsCommands)
    val items = if (dismissed == text) null else commandItems(text, provider, commands.commands)

    fun edit(next: String) {
        setText(next)
        error = null
    }
    val dictate = rememberDictate(dictation, input, possible = canDictate(remote) && !disabled && !busy, instead = { focus.requestFocus() })
    SideEffect {
        handle?.apply {
            onQuote = { md ->
                scope.launch {
                    // Keep the words already spoken; a dictation update mustn't overwrite the quote.
                    if (!dictation.finish()) return@launch
                    val next = appendQuote(value.text, md)
                    value = TextFieldValue(next.text, TextRange(next.end))
                    runCatching { focus.requestFocus() }
                }
            }
            onRestore = { back ->
                val before = value
                val beforeImages = images
                val beforeFiles = files
                val merged = returnedDraft(before.text, beforeImages, back)
                error = null
                setText(merged.first)
                images = merged.second
                val restored = FileUploads.restore(back.files)
                files = beforeFiles + restored
                ({
                    value = before
                    images = beforeImages
                    // The send goes back to the outbox with its copies.
                    files = beforeFiles
                    FileUploads.forget(restored)
                })
            }
            onCompose = { page, earlier -> setText(composeInto(value.text, page, earlier)) }
            onFocus = { runCatching { focus.requestFocus() } }
            onSettingsOn = { to -> switched(settings, to) }
            onOpenSettings = { sheet = ComposerSheetKind.Settings }
            onOpenUsage = { sheet = ComposerSheetKind.Usage }
            onOpenAttach = { sheet = ComposerSheetKind.Add }
            onDictate = dictate
            this.usage = ComposerUsage(provider, usage.usage)
            line = composerLine(settings, modelLabel, context)
        }
    }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    /** The desktop's runCommand for a composer setting; with no value, its picker opens. */
    suspend fun setting(name: String, args: String): RunResult {
        if (args.isEmpty() && name != "plan" && name != "fast") {
            sheet = when (name) {
                "permissions" -> ComposerSheetKind.Mode
                "effort" -> ComposerSheetKind.Effort
                else -> ComposerSheetKind.Model
            }
            return RunResult.Done
        }
        val known = when (name) {
            "model" -> lists.load(lists.offered).filterKeys { it in lists.offered }
            "effort", "fast" -> lists.load(listOf(provider))
            else -> emptyMap()
        }
        val result = composerCommand(
            name, args,
            CommandSettings(provider, agentProviders, settings.choice.model, settings.choice.fast, settings.interactionMode == "plan", known),
        )
        return when (result) {
            is CommandResult.Error -> RunResult.Failed(result.message)
            is CommandResult.Change -> {
                onSettings(withComposerChange(settings, result.change, known, switched))
                RunResult.Done
            }
        }
    }
    fun run(name: String, args: String) {
        scope.launch {
            val result = when {
                isComposerCommand(name) -> setting(name, args)
                name == "context" -> RunResult.Done.also { sheet = ComposerSheetKind.Usage }
                onCommand != null -> try { onCommand(name, args) } catch (e: CancellationException) { throw e } catch (e: Exception) { RunResult.Failed(e.message ?: e.toString()) }
                else -> RunResult.Failed("That works in a thread.")
            }
            when (result) {
                is RunResult.Failed -> error = result.message
                RunResult.Done -> setText("")
                RunResult.Kept -> {}
            }
        }
    }
    fun pick(item: MenuItem) {
        if (item.kind == "relay") {
            // Required values are picked or typed after it; the rest run now.
            if (item.name == "btw") return edit("/btw ")
            if (item.args?.startsWith("<") == true && !isComposerCommand(item.name)) return edit("/${item.name} ")
            return run(item.name, "")
        }
        if (item.kind == "command") return edit("/${item.name} ")
        // A skill goes in where it was typed, as `$name`.
        edit(text.replace(skillTail) { "${it.groupValues[1]}\$${item.name} " })
    }

    val empty = text.isBlank() && images.isEmpty() && files.isEmpty() && computerShots.isEmpty()
    val steering = running && !empty
    val target = "${computer.orEmpty()}:${sendTarget ?: draftKey ?: projectId}"
    val retryingScheduled = failedSend?.let { it.sendAt != null && sameDraft(it.draft, ComposeDraft(target, text.trim(), settings, images)) } == true

    /** `written` is the draft when it hasn't reached `text` yet. */
    fun send(delivery: String? = null, sendAt: Long? = null, written: String = text) {
        // Sending mid-dictation waits for the last words to land in the draft.
        if (dictation.activeNow) {
            scope.launch { if (dictation.finish()) send(delivery, sendAt, input.dictated) }
            return
        }
        if ((written.isBlank() && images.isEmpty() && files.isEmpty() && computerShots.isEmpty()) || busy) return
        val sendingFiles = files
        val pickedFiles = sendingFiles.mapNotNull { picked[it] }
        filesRefusal(pickedFiles, client.takeIf { online })?.let {
            error = it
            return
        }
        // A video still compressing goes once it's done.
        if (pickedFiles.any { it.phase == FilePhase.Preparing }) {
            busy = true
            scope.launch {
                try {
                    FileUploads.prepared(sendingFiles)
                } finally {
                    busy = false
                }
                send(delivery, sendAt, written)
            }
            return
        }
        val held = (overview?.bridge ?: 1) >= Bridge.DRAFTS && hasExtras(shared?.extras)
        val draft = written.trim()
        when (val command = draftCommand(draft, provider, commands.commands)) {
            is DraftCommand.Run -> return run(command.name, command.args)
            is DraftCommand.Refused -> {
                error = command.why
                return
            }
            DraftCommand.Message -> {}
        }
        val attempt = composeAttempt(
            ComposeDraft(target, draft, settings, images),
            delivery = if (sendAt != null) null else if (running) delivery ?: "queue" else null,
            sendAt = sendAt,
            failed = if (delivery == null && sendAt == null) failedSend else null,
            createId = { UUID.randomUUID().toString() },
        )
        failedSend = null
        busy = true
        error = null
        // Cleared as it goes, so leaving mid-send doesn't keep it as a draft; it comes back if sending fails.
        val pc = computer
        val key = draftKey
        if (pc != null && key != null) drafts.hold(pc, key)
        setText("")
        val sentImages = images
        val sentFiles = FileUploads.take(sendingFiles)
        // The draft ignored edits while the send held it: what was typed meanwhile goes in now,
        // before the draft's own update reaches the composer and would clear it.
        fun keepTyped() {
            if (pc != null && key != null) drafts.editText(pc, key, value.text)
        }
        scope.launch {
            try {
                if (!onSend(Composed(attempt, held, sentFiles))) {
                    if (pc != null && key != null) drafts.release(pc, key)
                    if (value.text.isEmpty()) setText(draft)
                    keepTyped()
                } else {
                    images = images.filter { it !in sentImages }
                    files = files.filter { it !in sendingFiles }
                    FileUploads.forget(sendingFiles)
                    if (pc != null && key != null) drafts.went(pc, key, draft, held)
                    keepTyped()
                }
            } catch (e: CancellationException) {
                // Left mid-send: the draft is let go with the message back in it, and as it may
                // have gone, sending it again reuses this attempt.
                if (pc != null && key != null) drafts.release(pc, key)
                val typed = value.text.trim()
                setText(if (typed.isNotEmpty()) "$draft\n\n$typed" else draft)
                keepTyped()
                failedSend = attempt
                throw e
            } catch (e: Exception) {
                if (pc != null && key != null) drafts.release(pc, key)
                failedSend = attempt
                if (value.text.isEmpty()) setText(draft)
                keepTyped()
                error = e.message ?: e.toString()
            } finally {
                busy = false
            }
        }
    }

    val pickers = rememberImagePickers(
        onPicked = { picked -> images = (images + picked) },
        onError = { error = it },
        onFiles = { uris, video ->
            val failed = unsent.error
            files = files + FileUploads.pick(appContext, remote, uris, video) { failed.value = it }
        },
    )
    val stop = running && empty && onStop != null
    val plan = settings.interactionMode == "plan"

    val effort = settings.choice.reasoningEffort
    val choices = ModelChoices(
        provider, modelLabel,
        effort = shortEffortLabel(effort).takeIf { effort.isNotEmpty() || offersEffort(provider, settings.choice.model, catalogs) },
        fast = agentInfo(provider)?.fast == true && settings.choice.fast,
        plan = plan,
    )
    val covered = sheet != null || drawingOn != null
    val writing = rememberTyping(typing, held = covered)
    KeepKeyboard(typing, held = covered)
    val expanded = (writing && !collapsed) || !empty || dictation.active
    SideEffect { handle?.expanded = expanded }
    val windowInfo = LocalWindowInfo.current
    val keyboard = LocalSoftwareKeyboardController.current

    /** Starts a command or a skill from the sheet; the keyboard can only come once the sheet's window has gone. */
    fun begin(next: String) {
        edit(next)
        scope.launch {
            snapshotFlow { windowInfo.isWindowFocused && sheet == null }.first { it }
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    Column(
        modifier.fillMaxWidth().background(c.surface).padding(horizontal = 15.dp).padding(top = 8.dp, bottom = COMPOSER_BOTTOM).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Without a screen to hold them, the plan's limits sit at the end of the line over the box, away from the thumb.
        if (settingsUp) above()
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { above() }
            UsageRings(provider, usage.usage, onClick = { sheet = ComposerSheetKind.Usage })
        }
        if (items != null) CommandMenu(items, commands.loading, commands.failed, ::pick)
        drawingOn?.let { image ->
            DrawOnImage(
                image,
                onDrawn = { drawn -> images = images.map { if (it === image) drawn else it } },
                onError = { error = it },
                onClose = { drawingOn = null },
            )
        }
        val pc = computer
        if (pc != null && draftKey != null) DraftNotes(drafts, pc, draftKey, shared)
        (error ?: dictation.error)?.let { e ->
            Text(
                e + if (error != null && retryingScheduled) " Tap send to retry at the original time." else "",
                Modifier.padding(horizontal = 16.dp).pressable(onClick = {
                    if (error == null) return@pressable dictation.dictation.clearError()
                    error = null
                    dismissed = text
                }, scale = false),
                style = RelayTheme.type.meta, color = c.danger,
            )
        }
        val attached = images.isNotEmpty() || computerShots.isNotEmpty() || files.isNotEmpty()
        val scrollTrace = LocalScrollTrace.current
        ComposerBox(
            expanded = expanded,
            choices = choices,
            onAdd = { sheet = ComposerSheetKind.Add },
            onModel = { sheet = ComposerSheetKind.Model },
            onEffort = { sheet = ComposerSheetKind.Effort },
            onPlan = { sheet = ComposerSheetKind.Settings },
            modifier = boxModifier,
            attachments = if (!attached) null else ({
                ImageStrip(
                    images, computerShots, onRemove = { i -> images = images.filterIndexed { j, _ -> j != i } },
                    onEdit = { i -> drawingOn = images.getOrNull(i) },
                    files = files.mapNotNull { picked[it] },
                    onRemoveFile = { key ->
                        FileUploads.remove(key)
                        files = files - key
                    },
                    onRetryFile = { FileUploads.retry(it, remote) },
                )
            }),
            field = {
                ComposerField(
                    field, placeholder ?: "Message ${agentName(provider)}", focus,
                    onFocus = {
                        if (typing != it) scrollTrace?.event("composer_focus", "focused" to it)
                        typing = it
                    },
                    onEdit = { error = null },
                    onImages = { pickers.receive(it) },
                )
            },
            buttons = {
                SendButtons(
                    SendState(
                        empty = empty, running = running, stop = stop, steering = steering, retryingScheduled = retryingScheduled,
                        enabled = !disabled && !busy, dictating = dictation.active, discardable = dictation.listening,
                    ),
                    onSteer = { send("steer") },
                    onSend = { if (stop) onStop?.invoke() else send() },
                    onLater = { sheet = ComposerSheetKind.Later },
                    onDiscardDictation = { dictation.dictation.cancel() },
                    mic = if (!canDictate(remote)) null else ({ strong ->
                        DictationButton(remote, dictation.owner, target = { input }, enabled = !disabled && !busy, strong = strong)
                    }),
                    newThread = newThreadSend,
                )
            },
        )
    }

    sheet?.let { open ->
        ComposerSheetHost(
            open,
            onOpen = { sheet = it },
            settings, onSettings, modelLabel = handle?.line?.model ?: modelLabel, lists, switched, client, online, projectId,
            readOnly = (overview?.bridge ?: 1) >= Bridge.READ_ONLY_MODE,
            attach = AttachActions(
                library = { pickers.library() }, camera = { pickers.camera() }, files = { pickers.files() }, paste = { pickers.paste() },
            ),
            commands = when {
                text.isEmpty() -> AddCommands("Commands", "Relay's and the agent's, or type /") { begin("/") }
                agentInfo(provider)?.skills == true -> AddCommands("Skills", "Add one to this message, or type $") { begin("${text.trimEnd()} $") }
                else -> null
            },
            onLater = { send(null, it) },
            usage = usage, context = context, cost = cost,
            onCompact = onCommand?.let { run ->
                suspend {
                    val result = run("compact", "")
                    if (result is RunResult.Failed) error = result.message
                }
            },
        )
    }
}
