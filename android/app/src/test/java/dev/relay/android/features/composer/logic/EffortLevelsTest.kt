package dev.relay.android.features.composer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffortLevelsTest {
    private val five = listOf("low", "medium", "high", "xhigh", "max")

    @Test fun `a model without efforts has no levels`() {
        assertNull(effortLevels(emptyList(), null))
        assertNull(effortLevels(emptyList(), "high"))
        assertNull(effortLevels(listOf(""), "high"))
    }

    @Test fun `a known default keeps the model's own levels and marks one`() {
        for (levels in listOf(listOf("low", "high"), listOf("low", "medium", "high"), five, five + "ultra")) {
            val stops = effortLevels(levels, "high")!!
            assertEquals(levels, stops.values)
            assertEquals(levels.indexOf("high"), stops.default)
            assertEquals(levels.indices.toList(), levels.map(stops::indexOf))
            assertTrue(stops.choosable)
        }
    }

    @Test fun `Default rides on its level, named after it`() {
        val stops = effortLevels(five, "medium")!!
        assertEquals(1, stops.indexOf(""))
        assertEquals("Medium", stops.label(""))
        assertTrue(stops.followsDefault(""))
        assertFalse(stops.followsDefault("medium"))
    }

    @Test fun `an unknown default is a stop of its own, first`() {
        for (default in listOf(null, "", "turbo")) {
            val stops = effortLevels(listOf("low", "high"), default)!!
            assertEquals(listOf("", "low", "high"), stops.values)
            assertNull(stops.default)
            assertEquals(0, stops.indexOf(""))
            assertEquals("Default", stops.label(""))
            assertEquals(2, stops.indexOf("high"))
        }
    }

    @Test fun `a level the model lacks shows as its default`() {
        val stops = effortLevels(listOf("low", "high"), "high")!!
        assertEquals(1, stops.indexOf("ultra"))
        assertEquals("High", stops.label("ultra"))
        assertTrue(stops.followsDefault("ultra"))
    }

    @Test fun `one level leaves nothing to choose unless Default differs from it`() {
        assertFalse(effortLevels(listOf("high"), "high")!!.choosable)
        assertTrue(effortLevels(listOf("high"), null)!!.choosable)
    }

    @Test fun `repeated levels are one stop`() {
        assertEquals(listOf("low", "high"), effortLevels(listOf("low", "high", "low"), "low")!!.values)
    }

    @Test fun `the chip shows only where an effort can be picked`() {
        val lists = mapOf(
            "opencode" to listOf(AgentModel("a", "A"), AgentModel("b", "B", efforts = listOf("low", "high"))),
            "cursor" to listOf(AgentModel("auto", "Auto")),
        )
        assertFalse(offersEffort("opencode", "a", lists))
        assertTrue(offersEffort("opencode", "b", lists))
        assertTrue(offersEffort("opencode", "", lists))
        assertFalse(offersEffort("cursor", "", lists))
        assertFalse(offersEffort("cursor", "", emptyMap()))
        assertTrue(offersEffort("claude", "opus", emptyMap()))
    }
}
