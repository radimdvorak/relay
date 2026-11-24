package dev.relay.android.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.relay.android.lib.navigation.NEW_THREAD_GREETING
import dev.relay.android.ui.EmptyGreeting
import dev.relay.android.ui.Text
import dev.relay.android.ui.sharedBox
import dev.relay.android.ui.theme.RelayTheme

/** Nothing open: the greeting, or that the filter hides what there is. */
@Composable
internal fun ActivityEmpty(filtered: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!filtered) return@Column EmptyGreeting(Modifier.sharedBox(NEW_THREAD_GREETING, RectangleShape))
        EmptyGreeting(text = "Nothing open here")
        Text(
            "No open thread matches the filter.",
            style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft, textAlign = TextAlign.Center,
        )
    }
}
