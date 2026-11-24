package dev.relay.android.ui

import kotlin.math.roundToInt

/** Below this a tab is too small to hit, so the bar goes without labels. */
internal const val BAR_MIN_TAB_DP = 52f

/**
 * How [full] pixels split between tabs of these [shares]: in proportion, [gap] apart, the last one
 * taking what rounding leaves, so the bar always ends exactly at its edge however the springs swing.
 */
internal fun barWidths(full: Int, gap: Int, shares: List<Float>): List<Int> {
    val room = (full - gap * (shares.size - 1)).coerceAtLeast(0)
    val total = shares.sum().takeIf { it > 0f } ?: return shares.map { 0 }
    val widths = shares.map { (room * it / total).roundToInt() }.toMutableList()
    widths[widths.lastIndex] = room - widths.dropLast(1).sum()
    return widths
}

/** What the chosen tab takes to hold its label, or null where that leaves the others under [min]. */
internal fun labelledTab(full: Int, gap: Int, tabs: Int, widestContent: Int, min: Float): Float? {
    val room = full - gap * (tabs - 1)
    val others = (room - widestContent).toFloat() / (tabs - 1).coerceAtLeast(1)
    return widestContent.toFloat().takeIf { others >= min }
}
