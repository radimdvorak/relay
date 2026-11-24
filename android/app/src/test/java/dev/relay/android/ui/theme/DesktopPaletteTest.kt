package dev.relay.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPaletteTest {
    private val light = Color(0xFFE1E1E5)

    @Test
    fun aChromeAllButTheSurfaceStepsOffTheWayItAlreadyLeans() {
        val surface = Color(0xFF1E1E21)
        val lighter = Color(0xFF232326).standingOff(surface, light)
        assertTrue(lighter.red - surface.red > 0.035f)
        val darker = Color(0xFF1B1B1E).standingOff(surface, light)
        assertTrue(surface.red - darker.red > 0.035f)
        // The very same colour: toward the text, on light themes as well.
        assertTrue(surface.standingOff(surface, light).luminance() > surface.luminance())
        assertTrue(Color.White.standingOff(Color.White, Color.Black).luminance() < 1f)
    }

    @Test
    fun aChromeThatAlreadyStandsOffIsLeftAlone() {
        assertEquals(Color(0xFF30303A), Color(0xFF30303A).standingOff(Color(0xFF1E1E21), light))
        assertEquals(Color(0xFFECECEC), Color(0xFFECECEC).standingOff(Color(0xFFFDFDFD), Color.Black))
    }
}
