package dev.relay.android.features.composer.logic

private val commandName = Regex("^/([^\\s]+)")
private val skillCommand = Regex("^/skill:[^\\s]+(?:\\s|$)")

/** What sending a draft does with it. */
sealed interface DraftCommand {
    /** It goes out, as a message or as one the agent runs itself. */
    data object Message : DraftCommand

    /** Relay runs it here. */
    data class Run(val name: String, val args: String) : DraftCommand
    data class Refused(val why: String) : DraftCommand
}

fun draftCommand(draft: String, provider: String, commands: List<ProviderCommand>): DraftCommand {
    if (!startsWithCommand(draft)) return DraftCommand.Message
    val command = relayCommand(draft)
    // The agent runs `/goal` itself; it goes out as a message.
    if (command?.name == "goal" && provider != "codex" && provider != "claude") {
        return DraftCommand.Refused("Pick Codex or Claude Code to set a goal.")
    }
    if (command != null && command.name != "btw" && command.name != "goal") return DraftCommand.Run(command.name, command.args)
    if (command?.name == "btw" && command.args.isEmpty()) return DraftCommand.Refused("Add a question after /btw.")
    val refusal = if (command?.name == "btw") sideRefusal(provider) else null
    if (refusal != null) return DraftCommand.Refused(refusal)
    val name = commandName.find(draft)?.groupValues?.get(1)
    val agentCommand = agentInfo(provider)?.commandsAlone == true && commands.any { it.name == name }
    val skill = agentInfo(provider)?.skills == true && skillCommand.containsMatchIn(draft)
    if (command == null && !agentCommand && !skill) return DraftCommand.Refused(
        "Choose a command from the menu. Relay actions run on their own; add instructions after a skill or an agent's command.",
    )
    return DraftCommand.Message
}
