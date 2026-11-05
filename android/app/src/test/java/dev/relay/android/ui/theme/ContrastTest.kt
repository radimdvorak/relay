package dev.relay.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private fun ratio(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `the middle leaves the colours alone`() {
        assertSame(HextaDark, HextaDark.withContrast(DEFAULT_CONTRAST))
    }

    @Test
    fun `more contrast makes secondary text easier to read in both kinds`() {
        for (base in listOf(HextaDark, HextaLight)) {
            val high = base.withContrast(100)
            val low = base.withContrast(0)
            assertTrue(ratio(high.mutedForeground, high.background) > ratio(base.mutedForeground, base.background))
            assertTrue(ratio(high.faint, high.background) > ratio(base.faint, base.background))
            assertTrue(ratio(low.faint, low.background) < ratio(base.faint, base.background))
            assertEquals(base.foreground, high.foreground)
            assertEquals(base.background, high.background)
        }
    }

    @Test
    fun `a see-through hairline shows twice as much at full contrast`() {
        val high = HextaDark.withContrast(100)
        assertEquals(HextaDark.border.alpha * 2, high.border.alpha, 0.01f)
    }
}
