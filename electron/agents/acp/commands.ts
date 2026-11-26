import { homedir } from "node:os";
import type { ProviderCommand } from "../../../shared/commands";
import type { AcpProfile } from "./profiles";
import { parseUpdate } from "./protocol";
import { skillCommands } from "../skill-presentation";
import type { AcpAgent } from "./sessions";

/** Who lists commands: an agent, as one of its accounts, which each have their own skills. */
type Lister = Pick<AcpProfile, "provider" | "accountKey">;

/** The slash commands each agent listed last, in any of its sessions. */
const heard = new Map<string, ProviderCommand[]>();

/** What each agent listed last in a folder; a helper's temporary one drops out again. */
const heardByFolder = new Map<string, ProviderCommand[]>();
const FOLDERS_KEPT = 50;

const listerKey = ({ provider, accountKey }: Lister) =>
  `${provider}\0${accountKey ?? ""}`;

const folderKey = (lister: Lister, cwd: string) =>
  `${listerKey(lister)}\0${cwd}`;

/**
 * Keeps what an agent listed in a folder. The home folder's list stays: it's
 * what a folder the agent hasn't opened yet offers.
 */
function keepForFolder(key: string, commands: ProviderCommand[]) {
  heardByFolder.delete(key);
  heardByFolder.set(key, commands);
  if (heardByFolder.size <= FOLDERS_KEPT) return;
  const atHome = `\0${homedir()}`;
  const oldest = [...heardByFolder.keys()].find((k) => !k.endsWith(atHome));
  if (oldest) heardByFolder.delete(oldest);
}

/**
 * Keeps the commands an `available_commands_update` lists. One named `$name`
 * is a skill, as T3 Code reads ACP agents' lists; the first of a name counts.
 * A `config_option_update` refreshes the session's settings, so models that
 * arrive after the session opens are available to the catalog.
 */
export function hearCommands(
  lister: Lister,
  raw: unknown,
  { agent, cwd }: { agent?: AcpAgent; cwd?: string } = {},
) {
  const update = parseUpdate(raw);
  if (update?.sessionUpdate === "config_option_update") {
    if (agent?.session) agent.session.settings.configOptions = update.configOptions;
    return;
  }
  if (update?.sessionUpdate !== "available_commands_update") return;
  const seen = new Set<string>();
  const commands = update.availableCommands.flatMap(
    (command): ProviderCommand[] => {
      const name = command.name.replace(/^\//, "").trim();
      if (!name || seen.has(name.toLowerCase())) return [];
      seen.add(name.toLowerCase());
      const description = (command.description ?? "").slice(0, 300);
      if (name.startsWith("$"))
        return name.length > 1
          ? skillCommands([
              {
                name: name.slice(1),
                path: `acp://skill/${encodeURIComponent(name.slice(1))}`,
                scope: "agent",
                enabled: true,
                ...(description.trim() ? { description } : {}),
              },
            ])
          : [];
      return [
        {
          name,
          description,
          source: "other",
          ...(command.input?.hint ? { argumentHint: command.input.hint } : {}),
        },
      ];
    },
  );
  heard.set(listerKey(lister), commands);
  if (cwd) keepForFolder(folderKey(lister, cwd), commands);
}

export const heardCommands = (lister: Lister) => heard.get(listerKey(lister));

export const heardInFolder = (lister: Lister, cwd: string) =>
  heardByFolder.get(folderKey(lister, cwd));
