package dev.relay.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.relay.android.R

/** Between regular and medium: Hexta's "book". */
val Book = FontWeight(520)

private fun inter(weight: FontWeight) = Font(
    R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Inter = FontFamily(
    inter(FontWeight.Normal),
    inter(Book),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
)

/** Newsreader's optical size axis set for display sizes, where its contrast and spacing are drawn for. */
private fun newsreader(weight: FontWeight) = Font(
    R.font.newsreader_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", 24f),
    ),
)

/** The display serif: the home greeting, the drawer's title, empty states. */
val Newsreader = FontFamily(newsreader(FontWeight.Normal), newsreader(FontWeight.Medium))

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/** The type scale in docs/expressive/design-brief.md ("Type"), plus the old Hexta names as aliases. */
@Immutable
data class RelayType(
    /** Newsreader 27/34: the empty home's line, empty states. The drawer's title is this at 22. */
    val greeting: TextStyle,
    /** 24/30 at 500: a tab's title on the chrome (Projects, PRs). */
    val title: TextStyle,
    /** 20/26 at 600: markdown H1–H2, headings inside a screen. */
    val heading: TextStyle,
    /** 17/22 at 500: the centred title of a sheet or dialog. */
    val sheetTitle: TextStyle,
    /** 15/24: answers, messages, paragraphs. */
    val body: TextStyle,
    /** 16/22: list row titles, the composer's text, inputs. */
    val row: TextStyle,
    /** 14/18: subtitles, descriptions, times. */
    val meta: TextStyle,
    /** 12/16: the smallest text, for dense rows. */
    val small: TextStyle,
    /** 12/16 at 500: chips, tooltips; section labels use it at 13. */
    val label: TextStyle,
    /** JetBrains Mono 13/20: code blocks, paths. */
    val code: TextStyle,
    /** JetBrains Mono 11/16: code in dense places (diff gutters, tool rows). */
    val codeSmall: TextStyle,
) {
    @Deprecated("use small", ReplaceWith("small"))
    val xs: TextStyle get() = small

    @Deprecated("use meta", ReplaceWith("meta"))
    val sm: TextStyle get() = meta

    @Deprecated("use body", ReplaceWith("body"))
    val base: TextStyle get() = body

    @Deprecated("use row", ReplaceWith("row"))
    val lg: TextStyle get() = row

    @Deprecated("use heading (20/26 at 600)")
    val xl: TextStyle get() = heading.copy(fontWeight = FontWeight.Medium)

    @Deprecated("use title", ReplaceWith("title"))
    val xxl: TextStyle get() = title

    @Deprecated("use greeting or title")
    val xxxl: TextStyle get() = title.copy(fontSize = 29.sp, lineHeight = 36.sp)

    @Deprecated("use greeting or title")
    val xxxxl: TextStyle get() = title.copy(fontSize = 35.sp, lineHeight = 42.sp)

    @Deprecated("use label", ReplaceWith("label"))
    val caption: TextStyle get() = label

    @Deprecated("use row", ReplaceWith("row"))
    val input: TextStyle get() = row

    @Deprecated("use code", ReplaceWith("code"))
    val mono: TextStyle get() = code

    @Deprecated("use codeSmall", ReplaceWith("codeSmall"))
    val monoSm: TextStyle get() = codeSmall
}

private val centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: TextUnit = 0.sp,
    family: FontFamily = Inter,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking,
    lineHeightStyle = centred,
)

val DefaultType = RelayType(
    greeting = style(27, 34, tracking = (-0.01).em, family = Newsreader),
    title = style(24, 30, FontWeight.Medium, (-0.02).em),
    heading = style(20, 26, FontWeight.SemiBold, (-0.01).em),
    sheetTitle = style(17, 22, FontWeight.Medium),
    body = style(15, 24),
    row = style(16, 22),
    meta = style(14, 18),
    small = style(12, 16),
    label = style(12, 16, FontWeight.Medium),
    code = style(13, 20, family = JetBrainsMono),
    codeSmall = style(11, 16, family = JetBrainsMono),
)
