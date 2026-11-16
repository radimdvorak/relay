package dev.relay.android.features.thread.logic

// shared/secret-request.ts: what a thread keeps of a secret an agent asked
// for (request_secret). Never the value, only how the request ended.

/** The one hidden question a secret request carries, under which its outcome is kept. */
const val SECRET_QUESTION_ID = "secret"

/** How a secret request ended: the row's label, and where the value went under it. */
data class SecretOutcome(val label: String, val note: String? = null)

/** [kept] is what the group's answers hold under [SECRET_QUESTION_ID]; null when the user declined. */
fun secretOutcome(name: String, kept: String?): SecretOutcome = when (kept) {
    null -> SecretOutcome("Declined \"$name\"")
    "Remembered" -> SecretOutcome("Used saved \"$name\"", "Saved for this project with Always, so there was no card")
    "Always" -> SecretOutcome(
        "Saved \"$name\"",
        "In a private file, not in the chat. Always: this project's agents get it without asking",
    )
    else -> SecretOutcome("Saved \"$name\"", "In a private file, not in the chat")
}

/** Where the card says the value goes. */
fun secretDestination(agent: String) =
    "It goes to a private file that $agent's commands read, not into the chat. Relay deletes the file after 24 hours."

/** shared/secret-request.ts secretNameKey: the same name however it is cased or spaced. */
fun secretNameKey(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

/**
 * shared/secret-request.ts alwaysExplained: what the card says Always does,
 * the names the project already has saved, and how long it lasts where the
 * computer can't seal it.
 */
fun alwaysExplained(name: String, others: List<String>, sessionOnly: Boolean): String = listOfNotNull(
    "Always also keeps it for this project: any agent here that asks for “$name” by exactly that name gets it without a card.",
    "This computer has no credential store, so it is kept only until Relay quits.".takeIf { sessionOnly },
    // The asked name itself can be saved by now, from another thread's card.
    others.filter { secretNameKey(it) != secretNameKey(name) }.takeIf { it.isNotEmpty() }
        ?.let { "Already saved here under other names: ${it.joinToString(", ")}." },
).joinToString(" ")
