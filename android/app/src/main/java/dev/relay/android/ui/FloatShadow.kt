package dev.relay.android.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

private val Cast = Shadow(radius = 14.dp, color = Color.Black, offset = DpOffset(0.dp, 4.dp), alpha = 0.12f)

/**
 * The shadow of an opaque [shape] floating over the surface. Not Android's elevation: under a tall
 * card low on the screen that leaves out a sliver at the bottom corners, which look broken.
 */
internal fun Modifier.floatShadow(shape: Shape): Modifier = dropShadow(shape, Cast)
