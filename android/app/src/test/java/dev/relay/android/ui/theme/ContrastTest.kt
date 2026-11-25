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
        assertSame(RelayPaperDark, RelayPaperDark.withContrast(DEFAULT_CONTRAST))
    }

    @Test
    fun `more contrast makes secondary text easier to read in both kinds`() {
        for (base in listOf(RelayPaperDark, RelayPaperLight)) {
            val high = base.withContrast(100)
            val low = base.withContrast(0)
            assertTrue(ratio(high.inkSoft, high.surface) > ratio(base.inkSoft, base.surface))
            assertTrue(ratio(high.inkFaint, high.surface) > ratio(base.inkFaint, base.surface))
            assertTrue(ratio(low.inkFaint, low.surface) < ratio(base.inkFaint, base.surface))
            assertEquals(base.ink, high.ink)
            assertEquals(base.surface, high.surface)
        }
    }

    @Test
    fun `a see-through hairline shows twice as much at full contrast`() {
        val high = RelayPaperDark.withContrast(100)
        assertEquals(RelayPaperDark.line.alpha * 2, high.line.alpha, 0.01f)
    }
}
