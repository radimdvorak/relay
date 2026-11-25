package dev.relay.android.features.dictation

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MicShapeTest {
    @Test
    fun aMicNotYetMeasuredHasAnEmptyOutline() {
        val outline = micShape { 0.5f }.createOutline(Size.Zero, LayoutDirection.Ltr, Density(2.625f))

        assertTrue(outline.bounds.isEmpty)
    }
}
