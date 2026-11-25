package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.relay.android.ui.theme.RelayTheme

/** The surface a screen's content lives on, its 24dp top corners rounding off the chrome above. */
@Composable
fun ContentSheet(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(RelayTheme.shapes.content).background(RelayTheme.colors.surface),
        content = content,
    )
}

/**
 * A whole screen in the new shell: the chrome behind, a [ChromeHeader] on it, the [ContentSheet]
 * filling the rest. The header slot is the caller's, so each screen picks its own buttons.
 */
@Composable
fun ChromeScreen(
    header: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    sheet: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(RelayTheme.colors.chrome)) {
        header()
        ContentSheet(Modifier.weight(1f).then(sheet), content)
    }
}
