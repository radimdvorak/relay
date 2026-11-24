package dev.relay.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme
import kotlin.math.roundToInt

/**
 * An input on the quiet container fill, 16dp corners, no outline until it has focus (a faint
 * ring) or an error (a danger ring and a shake). Multiline when `singleLine` is false.
 * `maxLength` shows a counter.
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 8,
    error: String? = null,
    maxLength: Int? = null,
    enabled: Boolean = true,
    mono: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    /** On a container-coloured card the surface instead, so the field shows. */
    fill: Color = RelayTheme.colors.container,
) {
    TakesKeyboard()
    val c = RelayTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RelayTheme.shapes.field
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error != null) shake.animateTo(0f, keyframes {
            durationMillis = 360
            -6f at 60; 6f at 120; -4f at 180; 4f at 240; -2f at 300
        })
    }
    val px = with(LocalDensity.current) { 1.dp.toPx() }
    val ringColor = when {
        error != null -> c.danger
        focused -> c.inkFaint
        else -> Color.Transparent
    }
    val text = (if (mono) RelayTheme.type.code.copy(fontSize = RelayTheme.type.row.fontSize) else RelayTheme.type.row).copy(color = c.ink)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = RelayTheme.type.meta, color = c.inkSoft)
        BasicTextField(
            value = value,
            onValueChange = { if (maxLength == null || it.length <= maxLength) onValueChange(it) },
            modifier = Modifier.fillMaxWidth()
                .offset { IntOffset((shake.value * px).roundToInt(), 0) }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            enabled = enabled,
            textStyle = text,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            // Prose unless the caller says otherwise: a capital after a full stop. Branches and paths pass None.
            keyboardOptions = if (keyboardOptions == KeyboardOptions.Default) {
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (singleLine) ImeAction.Done else ImeAction.Default)
            } else keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            cursorBrush = SolidColor(c.ink),
            decorationBox = { field ->
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(fill, shape)
                        .ring(ringColor, shape)
                        .padding(horizontal = 14.dp, vertical = if (singleLine) 0.dp else 12.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (leadingIcon != null) Icon(leadingIcon, null, tint = c.inkSoft, size = 20.dp, modifier = Modifier.padding(top = if (singleLine) 0.dp else 1.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null)
                            Text(placeholder, style = text, color = c.inkFaint, maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                        field()
                    }
                    if (trailing != null) trailing()
                }
            },
        )
        if (error != null || maxLength != null) Row(Modifier.fillMaxWidth()) {
            Text(error ?: "", Modifier.weight(1f), style = RelayTheme.type.small, color = c.danger)
            if (maxLength != null) Text(
                "${value.length}/$maxLength",
                style = RelayTheme.type.small,
                color = if (value.length >= maxLength) c.danger else c.inkFaint,
                textAlign = TextAlign.End,
            )
        }
    }
}
