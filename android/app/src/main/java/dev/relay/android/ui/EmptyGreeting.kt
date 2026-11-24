package dev.relay.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.R
import dev.relay.android.ui.theme.RelayTheme
import androidx.compose.material3.Icon as MaterialIcon

/** The empty home: Relay's mark in the accent over a line in the display serif. */
@Composable
fun EmptyGreeting(modifier: Modifier = Modifier, text: String = "What should we work on?") {
    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RelayMark(size = 36.dp)
        Text(text, style = RelayTheme.type.greeting, textAlign = TextAlign.Center)
    }
}

/** Relay's R, in the accent unless told otherwise. */
@Composable
fun RelayMark(modifier: Modifier = Modifier, size: Dp = 28.dp, tint: Color = RelayTheme.colors.accent) {
    MaterialIcon(painterResource(R.drawable.relay_mark), contentDescription = "Relay", modifier = modifier.size(size), tint = tint)
}
