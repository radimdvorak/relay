import type { ProviderCommand } from "../../../shared/commands";
import {
  presentSkill,
  skillCommands,
  type AgentSkill,
} from "../skill-presentation";

/** The CLI's own slash commands. It lists its skills among them, unmarked. */
const ownCommands = new Set([
  "boost",
  "browser",
  "compact",
  "goal",
  "grill-me",
  "learn",
  "plan",
  "schedule",
  "swarm",
  "teamwork",
]);

/**
 * The CLI's list as Relay offers it: its commands, then its skills. `found`
 * are the skills read from folders, which know where each one lives. The CLI
 * loads more than those folders, and drops some, so what it listed for this
 * very folder is the whole list; without one, `found` fills in.
 */
export function antigravityOffer(
  listed: { commands: ProviderCommand[]; forThisFolder: boolean },
  found: AgentSkill[],
): ProviderCommand[] {
  const isOwn = (command: ProviderCommand) => ownCommands.has(command.name);
  const known = new Map(found.map((skill) => [skill.name, skill]));
  const skills = listed.commands
    .filter((command) => !isOwn(command))
    .map((command): ProviderCommand => {
      const skill = known.get(command.name);
      return skill ? presentSkill(skill) : { ...command, skill: true };
    });
  const names = new Set(skills.map((skill) => skill.name));
  const unlisted = found.filter((skill) => !names.has(skill.name));
  return [
    ...listed.commands.filter(isOwn),
    ...skills,
    ...(listed.forThisFolder ? [] : skillCommands(unlisted)),
  ];
}
