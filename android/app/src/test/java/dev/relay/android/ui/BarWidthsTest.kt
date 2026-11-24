package dev.relay.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarWidthsTest {
    @Test
    fun alwaysFillsTheBarExactly() {
        for (shares in listOf(listOf(1f, 1f, 1f), listOf(1.7f, 1f, 1f), listOf(1.83f, 0.91f, 1.04f))) {
            for (full in listOf(1000, 1001, 1146)) {
                assertEquals(full, barWidths(full, 18, shares).sum() + 2 * 18)
            }
        }
    }

    @Test
    fun theChosenTabTakesMore() {
        val (a, b, c) = barWidths(1000, 10, listOf(2f, 1f, 1f))
        assertEquals(490, a)
        assertEquals(245, b)
        assertEquals(245, c)
    }

    @Test
    fun labelsGoWhereTheOthersWouldGetTooSmall() {
        assertEquals(400f, labelledTab(1000, 10, 3, 400, 150f))
        assertNull(labelledTab(600, 10, 3, 400, 150f))
    }
}
