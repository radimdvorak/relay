package dev.relay.android.features.thread

// The desktop's AsyncQuestionCards (src/features/thread): questions an agent
// left in its answer while it kept working. They stay answerable after the
// turn ends; dismissing one hides it without telling the agent anything.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import dev.relay.android.features.renders.AskPage
import dev.relay.android.features.thread.logic.SECRET_QUESTION_ID
import dev.relay.android.features.thread.logic.agentName
import dev.relay.android.features.thread.logic.secretOutcome
import dev.relay.android.remote.Bridge
import dev.relay.android.remote.Remote
import dev.relay.android.remote.model.RemoteJson
import dev.relay.android.remote.model.AsyncAgentQuestions
import dev.relay.android.remote.model.ChatMessage
import dev.relay.android.remote.model.SavedSecrets
import dev.relay.android.ui.Haptic
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.rememberHaptics
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.MessageCircleQuestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val PAGE_QUESTION_ID = "page"

@Composable
fun AsyncQuestions(chatId: String, message: ChatMessage) {
    for (group in message.questions.orEmpty()) {
        if (group.answers != null) Answered(group) else Open(chatId, message, group)
    }
}

@Composable
private fun Open(chatId: String, message: ChatMessage, group: AsyncAgentQuestions) {
    val remote = LocalThreadRemote.current ?: return
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun run(call: suspend () -> Unit, after: () -> Unit = {}) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                call()
                after()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                busy = false
            }
        }
    }
    val many = group.questions.size > 1
    val title = group.page?.title ?: "${agentName(message.provider)} has ${if (many) "questions" else "a question"}"
    // A tap, not Success: that one is the turn's end.
    val sent = { haptics.perform(Haptic.Light) }
    fun dismiss(dismissed: Boolean) =
        run({ remote.desktop("setProjectChatQuestionDismissed", chatId, message.id, group.id, dismissed) })
    val c = RelayTheme.colors
    val secret = group.secret
    // Decline stands where the other cards have Dismiss.
    if (secret != null && group.dismissed != true) SecretRequestCard(
        secret, agentName(message.provider), busy, error, savedSecrets(remote, chatId),
    ) { value, always ->
        run({
            remote.desktop(
                "answerProjectChatQuestion", chatId, message.id, group.id,
                mapOf("kind" to "secret", "value" to value) + if (always) mapOf("remember" to true) else emptyMap(),
            )
        }, sent)
    } else if (group.dismissed != true) QuietCard(padding = 16.dp, gap = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TraceGap)) {
            Icon(TablerIcons.Outline.MessageCircleQuestion, null, size = 18.dp, tint = c.waiting)
            Text(title, Modifier.weight(1f), style = RelayTheme.type.body.copy(fontWeight = FontWeight.SemiBold))
            TextAction("Dismiss", { dismiss(true) }, enabled = !busy)
        }
        Hint("Answer whenever you're ready.")
        val page = group.page
        // What the agent asks with its page, unless it's only the title again.
        group.questions.firstOrNull()?.question?.takeIf { page != null && it.isNotEmpty() && it != page.title }?.let { Hint(it) }
        // AskPage shares out the height it's given; in the thread's list that has to be bounded.
        if (page != null) Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.7f).dp)) {
            AskPage(remote, chatId, page, busy, onAnswer = { answer ->
                run({
                    remote.desktop(
                        "answerProjectChatQuestion", chatId, message.id, group.id,
                        mapOf("kind" to "page", "answer" to answer),
                    )
                }, sent)
            })
        } else if (group.questions.isNotEmpty()) Questions(group.questions, busy, deferred = true, capped = false) { answers ->
            run({
                remote.desktop(
                    "answerProjectChatQuestion", chatId, message.id, group.id,
                    mapOf("kind" to "question", "answers" to answers),
                )
            }, sent)
        }
        error?.let { Hint(it, color = c.danger) }
    } else Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Fold("Dismissed ${if (many) "questions" else "question"}", Modifier.weight(1f, fill = false)) {
            for (q in group.questions) Hint(q.question)
        }
        TextAction("Reopen", { dismiss(false) }, enabled = !busy, color = c.ink)
        error?.let { Hint(it, color = c.danger) }
    }
}

/**
 * What the thread's project has saved with Always, for the card to say what
 * Always does. Null while it loads, where the computer is too old to remember
 * secrets, or when it can't be read: the card then offers Decline and Save.
 */
@Composable
private fun savedSecrets(remote: Remote, chatId: String): SavedSecrets? {
    val supported = (remote.overview.collectAsState().value?.bridge ?: 1) >= Bridge.SECRET_ALWAYS
    var saved by remember(chatId) { mutableStateOf<SavedSecrets?>(null) }
    LaunchedEffect(chatId, supported) {
        saved = if (!supported) null else try {
            RemoteJson.decodeFromJsonElement(SavedSecrets.serializer(), remote.desktop("threadSecrets", chatId))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    return saved
}

@Composable
private fun Answered(group: AsyncAgentQuestions) {
    val answers = group.answers.orEmpty()
    group.secret?.let {
        SecretOutcomeRow(secretOutcome(it.name, answers[SECRET_QUESTION_ID]?.firstOrNull()))
        return
    }
    val page = group.page
    if (page != null) {
        val answer = answers[PAGE_QUESTION_ID]?.joinToString("\n")
        Fold("${if (answer != null) "Answered" else "Skipped"} \"${page.title}\"") {
            if (answer != null) SelectionContainer { Hint(answer) }
        }
        return
    }
    Fold("Answered ${if (group.questions.size > 1) "questions" else "question"}") {
        for (q in group.questions) Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Hint(q.question, color = RelayTheme.colors.ink)
            SelectionContainer { Hint(if (q.isSecret == true) "Hidden answer" else answers[q.id]?.joinToString(", ").orEmpty()) }
        }
    }
}

@Composable
private fun Fold(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Step(Modifier.pressable(onClick = { open = !open }, scale = false)) {
            StepIcon(TablerIcons.Outline.MessageCircleQuestion)
            StepText(label, Modifier.weight(1f, fill = false))
            StepChevron(open)
        }
        if (open) Column(
            Modifier.padding(start = TraceIndent, top = 2.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}
