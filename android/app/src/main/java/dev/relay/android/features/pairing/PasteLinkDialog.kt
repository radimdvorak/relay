package dev.relay.android.features.pairing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.relay.android.remote.PairingLink
import dev.relay.android.remote.parsePairingUrl
import dev.relay.android.ui.Text
import dev.relay.android.ui.ring
import dev.relay.android.ui.theme.Motion
import dev.relay.android.ui.theme.RelayTheme
import kotlin.math.roundToInt

/** ios PairingView's "Pairing link" alert: for when the clipboard holds something else. */
@Composable
fun PasteLinkDialog(onPair: (PairingLink) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun take() {
        val parsed = parsePairingUrl(text.trim())
        if (parsed == null) {
            error = "That isn't a Relay pairing link."
            return
        }
        onDismiss()
        onPair(parsed)
    }
    PairDialogCard(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Pairing link", style = PairType.dialogTitle)
            Text("Copy the link under the code in Relay's Settings → Phone.", style = PairType.text, color = RelayTheme.colors.inkSoft)
        }
        LinkField(text, { text = it; error = null }, error, ::take, focus)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PairButton("Cancel", onDismiss, outline = true, size = PairButtonSize.Dialog)
            PairButton("Pair", ::take, size = PairButtonSize.Dialog, enabled = text.isNotBlank())
        }
    }
}

/** Centred over a dimmed window, fading in from 96% scale. */
@Composable
private fun PairDialogCard(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = RelayTheme.colors
    val shape = RoundedCornerShape(17.9.dp)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(Motion.BASE, easing = Motion.EaseOutQuint)) }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .graphicsLayer {
                        alpha = enter.value
                        val s = 0.96f + 0.04f * enter.value
                        scaleX = s
                        scaleY = s
                    }
                    .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .background(c.raised, shape)
                    .ring(PairDialogRim, shape)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

/** The link's field: a ring that lights up with focus, red with a shake on something else's link. */
@Composable
private fun LinkField(text: String, onText: (String) -> Unit, error: String?, onGo: () -> Unit, focus: FocusRequester) {
    val c = RelayTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(PairCorner)
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
        focused -> Color(0xFF737373)
        else -> c.line
    }
    val style = PairType.field.copy(color = c.ink)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicTextField(
            text,
            onText,
            Modifier.fillMaxWidth().offset { IntOffset((shake.value * px).roundToInt(), 0) }.focusRequester(focus),
            textStyle = style,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            interactionSource = interaction,
            cursorBrush = SolidColor(c.ink),
            decorationBox = { field ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp)
                        .background(c.line.copy(alpha = c.line.alpha * 0.3f), shape)
                        .ring(ringColor, shape)
                        .then(if (focused && error == null) Modifier.ring(c.ink.copy(alpha = 0.15f), shape) else Modifier)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) Text("relay-remote://pair?…", style = style, color = c.inkFaint, maxLines = 1)
                    field()
                }
            },
        )
        if (error != null) Text(error, Modifier.fillMaxWidth(), style = PairType.small, color = c.danger)
    }
}
