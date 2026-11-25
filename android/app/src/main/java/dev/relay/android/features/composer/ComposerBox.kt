package dev.relay.android.features.composer

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.ComposerPill
import dev.relay.android.ui.ComposerPlaceholder
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The message box: a pill that grows into a card, with the attachments over the text and the
 * model's chips under it. The same field throughout, so focus, selection and what the keyboard
 * is composing carry over.
 */
@Composable
internal fun ComposerBox(
    expanded: Boolean,
    choices: ModelChoices,
    onAdd: () -> Unit,
    onModel: () -> Unit,
    onEffort: () -> Unit,
    onPlan: () -> Unit,
    field: @Composable () -> Unit,
    buttons: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    attachments: (@Composable () -> Unit)? = null,
) {
    ComposerPill(
        field = field,
        modifier = modifier.fillMaxWidth(),
        expanded = expanded,
        leading = { CircleButton(TablerIcons.Outline.Plus, "Attach and more", onAdd, size = CircleSize.Composer) },
        chips = { ModelChips(choices, onModel, onEffort, onPlan) },
        trailing = { buttons() },
        header = { attachments?.invoke() },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComposerField(
    field: TextFieldState,
    placeholder: String,
    focus: FocusRequester,
    onFocus: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onImages: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = RelayTheme.colors
    Box(modifier) {
        if (field.text.isEmpty()) ComposerPlaceholder(placeholder)
        BasicTextField(
            field,
            Modifier.fillMaxWidth().heightIn(min = 22.dp, max = 154.dp).focusRequester(focus)
                .onFocusChanged { onFocus(it.isFocused) }
                // Images from the keyboard's clipboard or a long-press Paste; text goes on into the field.
                .contentReceiver { content ->
                    if (!content.hasMediaType(MediaType.Image)) return@contentReceiver content
                    val uris = mutableListOf<Uri>()
                    val rest = content.consume { item -> item.uri?.also { uris += it } != null }
                    onImages(uris)
                    rest
                }
                .semantics { contentDescription = "Message" },
            textStyle = RelayTheme.type.row.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            inputTransformation = { onEdit() },
        )
    }
}

/**
 * Whether a focused field is being typed in. Back hides the keyboard but leaves the focus (the
 * cursor matters to dictation), so a field whose keyboard was put away counts as left until the
 * keyboard comes back. A hardware keyboard shows none and never counts as put away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberTyping(focused: Boolean, held: Boolean): Boolean {
    val keyboard = WindowInsets.isImeVisible
    var seen by remember { mutableStateOf(false) }
    var putAway by remember { mutableStateOf(false) }
    LaunchedEffect(focused, keyboard, held) {
        when {
            !focused -> {
                seen = false
                putAway = false
            }
            keyboard -> {
                seen = true
                putAway = false
            }
            // A sheet takes the keyboard for as long as it's up, and a moment past it.
            seen && !held -> {
                delay(200)
                putAway = true
            }
        }
    }
    return focused && !putAway
}

/** Brings the keyboard back once a sheet that took it ([held]) is gone, so a message half typed goes on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeepKeyboard(focused: Boolean, held: Boolean) {
    val keyboard = WindowInsets.isImeVisible
    val controller = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current
    val stillFocused by rememberUpdatedState(focused)
    var taken by remember { mutableStateOf(false) }
    LaunchedEffect(held) {
        if (held) {
            taken = keyboard
            return@LaunchedEffect
        }
        if (!taken) return@LaunchedEffect
        taken = false
        snapshotFlow { window.isWindowFocused }.first { it }
        if (stillFocused) controller?.show()
    }
}
