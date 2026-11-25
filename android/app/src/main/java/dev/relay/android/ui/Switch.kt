package dev.relay.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayTheme

/** A 44×26 switch: strong track when on, tool when off; the knob rides inside. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = RelayTheme.colors
    val haptics = rememberHaptics()
    val track by animateColorAsState(if (checked) c.strong else c.selected, MaterialTheme.motionScheme.fastEffectsSpec(), label = "track")
    val knob by animateDpAsState(if (checked) 18.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "knob")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .toggleable(checked, interaction, null, enabled, Role.Switch) {
                haptics.perform(Haptic.Selection)
                onCheckedChange(it)
            }
            .pressScale(interaction, enabled)
            .alpha(if (enabled) 1f else 0.5f)
            .size(44.dp, 26.dp)
            .background(track, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = knob).size(20.dp).shadow(1.dp, CircleShape)
                .background(knobFill(checked, c), CircleShape),
        )
    }
}

/** Off in dark, the raised fill is as dark as the track: the knob takes the soft ink there. */
private fun knobFill(checked: Boolean, c: RelayColors): Color = when {
    checked -> c.onStrong
    c.isDark -> c.inkSoft
    else -> c.raised
}
