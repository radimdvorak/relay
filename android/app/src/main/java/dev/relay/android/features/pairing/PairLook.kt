package dev.relay.android.features.pairing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.relay.android.ui.ContentColor
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.ring
import dev.relay.android.ui.theme.Inter
import dev.relay.android.ui.theme.JetBrainsMono
import dev.relay.android.ui.theme.RelayPaperDark
import dev.relay.android.ui.theme.RelayTheme

/*
 * Pairing's own look. It's drawn around the field and the camera and is kept as it was designed,
 * so it doesn't follow the app's palette, type scale or button shapes when those change.
 */

/** Relay's own colours, dark on black whatever the theme: the field glows best on black. */
internal val PairColors = RelayPaperDark.copy(
    surface = Color.Black,
    raised = Color(0xFF171717),
    tool = Color(0xFF262626),
    ink = Color(0xFFFAFAFA),
    inkSoft = Color(0xFFA1A1A1),
    inkFaint = Color(0xFF6B6B6B),
    strong = Color(0xFFE5E5E5),
    onStrong = Color(0xFF171717),
    accent = Color(0xFFE5E5E5),
    line = Color(0x26FFFFFF),
    danger = Color(0xFFFF6467),
)

/** Around the link dialog, fainter than an input's. */
internal val PairDialogRim = Color(0x1AFFFFFF)

internal object PairType {
    private val centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    private fun style(size: Int, line: Float, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = 0.sp) = TextStyle(
        fontFamily = Inter,
        fontSize = size.sp,
        lineHeight = (size * line).sp,
        fontWeight = weight,
        letterSpacing = tracking,
        lineHeightStyle = centred,
    )

    val heading = style(23, 1.3f, FontWeight.Medium, (-0.025).em)
    val dialogTitle = style(17, 1.35f, FontWeight.SemiBold, (-0.01).em)
    val text = style(13, 1.5f)
    val button = style(13, 1.4f, FontWeight.Medium)
    val small = style(11, 1.45f)
    val field = TextStyle(fontFamily = JetBrainsMono, fontSize = 16.sp, lineHeight = 20.sp, lineHeightStyle = centred)
}

internal val PairCorner = 10.2.dp
private val corner = RoundedCornerShape(PairCorner)

/** Large buttons run the width of the column; the dialog's are small. */
internal enum class PairButtonSize(val height: Dp, val padX: Dp) { Dialog(36.dp, 12.dp), Large(40.dp, 14.dp) }

@Composable
internal fun PairButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outline: Boolean = false,
    size: PairButtonSize = PairButtonSize.Large,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val c = RelayTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val takesTaps = enabled && !loading
    val lit = pressed && takesTaps
    val fill = when {
        !outline -> if (lit) c.strong.copy(alpha = 0.8f) else c.strong
        lit -> c.tool
        else -> c.line.copy(alpha = c.line.alpha * 0.3f).compositeOver(c.surface)
    }
    Box(
        modifier
            .pressable(onClick, enabled = takesTaps, interaction = interaction)
            .height(size.height)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(corner)
            .background(fill)
            .then(if (outline) Modifier.ring(c.line, corner) else Modifier)
            .padding(horizontal = size.padX),
        contentAlignment = Alignment.Center,
    ) {
        ContentColor(if (outline) c.ink else c.onStrong) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null || loading) ButtonGlyph(icon, loading)
                Text(text, style = PairType.button, color = if (outline) c.ink else c.onStrong, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ButtonGlyph(icon: ImageVector?, loading: Boolean) = AnimatedContent(
    loading,
    transitionSpec = {
        (fadeIn(tween(150)) + scaleIn(tween(150), 0.6f)) togetherWith (fadeOut(tween(100)) + scaleOut(tween(100), 0.6f))
    },
    label = "loading",
) { busy ->
    if (busy) Spinner(size = 16.dp)
    else if (icon != null) Icon(icon, null, size = 16.dp)
}

/** A square button over the field: close, update. */
@Composable
internal fun PairIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val c = RelayTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .pressable(onClick, interaction = interaction)
            .size(36.dp)
            .clip(corner)
            .background(if (pressed) c.ink.copy(alpha = 0.06f).compositeOver(c.tool) else c.tool)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint ?: c.ink) }
}
