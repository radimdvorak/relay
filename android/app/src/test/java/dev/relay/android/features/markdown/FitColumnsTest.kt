package dev.relay.android.features.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitColumnsTest {
    private fun fit(available: Int, vararg columns: ColumnText) = fitColumns(columns.toList(), available, roomySide = 12, tightSide = 8, widestScrolled = 200)

    @Test
    fun columnsThatFitKeepTheirOwnWidth() {
        assertEquals(FittedColumns(listOf(60, 100), side = 12), fit(400, ColumnText(60, 60), ColumnText(100, 72)))
    }

    @Test
    fun onlyTheWideColumnsWrapAndTheyFillTheWidth() {
        val fitted = fit(372, ColumnText(300, 90), ColumnText(40, 40), ColumnText(260, 80))

        assertEquals(FittedColumns(listOf(142, 40, 142), side = 8), fitted)
    }

    @Test
    fun theSidesGiveWayBeforeAnythingWraps() {
        assertEquals(FittedColumns(listOf(130, 130), side = 8), fit(300, ColumnText(130, 80), ColumnText(130, 80)))
    }

    @Test
    fun aTableThatOnlyJustFitsWrapsRatherThanScrolls() {
        val fitted = fit(300, ColumnText(300, 130), ColumnText(300, 130))

        assertTrue(fitted.texts.sum() + 4 * fitted.side <= 300)
    }

    @Test
    fun columnsThatCannotBeReadAtThisWidthScrollInstead() {
        val fitted = fit(372, *Array(7) { ColumnText(300, 72) })

        assertEquals(FittedColumns(List(7) { 200 }, side = 12), fitted)
    }
}
