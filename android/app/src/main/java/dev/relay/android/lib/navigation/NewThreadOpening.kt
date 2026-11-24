package dev.relay.android.lib.navigation

/** Shared-bounds key of the empty Home's greeting and the new-thread screen's, so it stays put between them. */
const val NEW_THREAD_GREETING = "new-thread-greeting"

/** How a new thread's composer opens: ready to type, on the Attach sheet, or listening. */
enum class ComposerStart { Type, Attach, Dictate }

/**
 * How a new thread's composer opens, and where it comes from: [rise] is how far over the window's
 * end Home's composer stands, in pixels, or null when no composer was there to grow out of.
 */
data class ComposerOpening(val start: ComposerStart = ComposerStart.Type, val rise: Int? = null)

/**
 * Home's composer leaves here what its plus or mic asked for, and the new-thread screen it opens
 * takes it. Not part of the route: a screen restored later shouldn't start the microphone again.
 */
object NextComposerOpening {
    private var asked = ComposerOpening()

    fun ask(opening: ComposerOpening) {
        asked = opening
    }

    fun take(): ComposerOpening = asked.also { asked = ComposerOpening() }
}
