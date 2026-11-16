package dev.relay.android.features.sketch

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SketchTest {
    private fun circle(radius: Float, sweep: Double = 2 * Math.PI) =
        (0..20).map { i -> val a = sweep * i / 20; Offset(100 + radius * cos(a).toFloat(), 100 + radius * sin(a).toFloat()) }

    @Test
    fun aRedactThatComesBackAroundCoversItsInside() {
        assertTrue(closesUp(Ink.Redact, circle(80f), reach = 20f))
        assertTrue(closesUp(Ink.Blur, circle(80f), reach = 20f))
    }

    @Test
    fun aPenLineOrAnOpenArcStaysALine() {
        assertFalse(closesUp(Ink.Pen, circle(80f), reach = 20f))
        assertFalse(closesUp(Ink.Redact, circle(80f, sweep = Math.PI), reach = 20f))
        // A scribble on one spot meets its start but went around nothing.
        assertFalse(closesUp(Ink.Redact, circle(5f), reach = 20f))
    }

    @Test
    fun aNewStrokeAfterUndoDropsWhatWasUndone() {
        val a = Stroke(Ink.Pen, 0, 4f, listOf(Offset.Zero))
        val b = a.copy(width = 8f)
        val c = a.copy(width = 12f)
        val undone = StrokeHistory(listOf(a, b)).undo()
        assertEquals(listOf(a), undone.strokes)
        assertEquals(listOf(a, b), undone.redo().strokes)
        val next = undone.add(c)
        assertEquals(listOf(a, c), next.strokes)
        assertFalse(next.canRedo)
    }
}
