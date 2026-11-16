package dev.relay.android.features.thread.logic

import org.junit.Test
import org.junit.Assert.assertEquals

/** Mirrors the desktop's rows (src/features/thread/AsyncQuestionCards) and iOS's SecretRequestsTests. */
class SecretRequestsTest {
    @Test
    fun `says how a secret request ended once`() {
        assertEquals(SecretOutcome("Declined \"npm token\""), secretOutcome("npm token", null))
        assertEquals(
            SecretOutcome("Saved \"npm token\"", "In a private file, not in the chat"),
            secretOutcome("npm token", "Saved"),
        )
        assertEquals(
            SecretOutcome(
                "Saved \"npm token\"",
                "In a private file, not in the chat. Always: this project's agents get it without asking",
            ),
            secretOutcome("npm token", "Always"),
        )
        assertEquals(
            SecretOutcome("Used saved \"npm token\"", "Saved for this project with Always, so there was no card"),
            secretOutcome("npm token", "Remembered"),
        )
    }

    @Test
    fun `tells where the value goes`() {
        assertEquals(
            "It goes to a private file that Claude's commands read, not into the chat. Relay deletes the file after 24 hours.",
            secretDestination("Claude"),
        )
    }
}
