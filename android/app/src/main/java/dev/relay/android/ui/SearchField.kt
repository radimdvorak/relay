package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Search
import io.github.lyxnx.compose.ui.tablericons.outline.X
import dev.relay.android.ui.theme.RelayTheme

/**
 * A fully round 44dp search field: a glass, the query, and a ✕ once there's something to clear.
 * Tool-filled by default (the drawer, lists); pass the raised fill when it sits on the chrome.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    focusRequester: FocusRequester? = null,
    fill: Color = RelayTheme.colors.tool,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
) {
    val c = RelayTheme.colors
    val text = RelayTheme.type.row.copy(color = c.ink)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        textStyle = text,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(c.ink),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().height(44.dp).background(fill, RelayTheme.shapes.circle)
                    .padding(start = 14.dp, end = if (value.isEmpty()) 14.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(TablerIcons.Outline.Search, null, tint = c.inkSoft, size = 20.dp)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = text, color = c.inkFaint, maxLines = 1)
                    field()
                }
                if (value.isNotEmpty()) CircleButton(
                    TablerIcons.Outline.X, "Clear", { onValueChange("") },
                    size = CircleSize.Small, tone = CircleTone.Clear,
                )
            }
        },
    )
}
