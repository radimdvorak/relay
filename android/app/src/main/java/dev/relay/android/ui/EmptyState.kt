package dev.relay.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.delay

/**
 * An empty state: the three-card fan (or a plain media tile), a title in the display serif, a
 * soft line and an optional action. The parts fade up 6dp in turn. The empty home uses
 * [EmptyGreeting] instead.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    fan: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) Rise(0) {
            if (fan) CardFan(icon) else MediaTile(icon, Modifier, 40.dp, RelayTheme.colors.inkSoft)
        }
        if (icon != null) Spacer(Modifier.height(if (fan) 20.dp else 16.dp))
        Rise(60) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = RelayTheme.type.greeting.copy(fontSize = 22.sp, lineHeight = 28.sp), textAlign = TextAlign.Center)
                if (description != null) Text(description, Modifier.width(300.dp), style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft, textAlign = TextAlign.Center)
            }
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Rise(110) { action() }
        }
    }
}

@Composable
private fun Rise(delayMs: Int, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(Unit) {
        if (reduced) p.snapTo(1f) else {
            delay(delayMs.toLong())
            p.animateTo(1f, tween(480, easing = dev.relay.android.ui.theme.Motion.EaseOutQuint))
        }
    }
    Box(
        Modifier
            .graphicsLayer {
                alpha = p.value
                translationY = (1f - p.value) * 6.dp.toPx()
            }
            .blur((2 * (1f - p.value)).dp),
    ) { content() }
}

/** Two cards tilted ±10° behind a front one, each 90% and 28% out to the side. */
@Composable
fun CardFan(icon: ImageVector, modifier: Modifier = Modifier, cardSize: Dp = 52.dp) {
    val c = RelayTheme.colors
    val w = cardSize
    val h = cardSize * 1.15f
    Box(modifier.width(w * 2f).height(h + 8.dp), contentAlignment = Alignment.Center) {
        for (side in listOf(-1, 1)) Box(
            Modifier
                .graphicsLayer {
                    rotationZ = 10f * side
                    translationX = side * w.toPx() * 0.28f
                    scaleX = 0.9f
                    scaleY = 0.9f
                }
                .size(w, h)
                .background(c.tool, RelayTheme.shapes.field),
        )
        Box(
            Modifier.size(w, h)
                .shadow(6.dp, RelayTheme.shapes.field, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f), spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f))
                .background(c.raised, RelayTheme.shapes.field),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.inkSoft, size = 20.dp) }
    }
}
