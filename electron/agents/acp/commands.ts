import type { ProviderCommand } from "../../../shared/commands";
import type { AcpProvider } from "./profiles";
import { parseUpdate } from "./protocol";
import { skillCommands } from "../skill-presentation";
import type { AcpAgent } from "./sessions";

/** The slash commands each agent listed last, in any of its sessions. */
const heard = new Map<AcpProvider, ProviderCommand[]>();

/** What each agent listed last in a folder; a helper's temporary one drops out again. */
const heardByFolder = new Map<string, ProviderCommand[]>();
const FOLDERS_KEPT = 50;

const folderKey = (provider: AcpProvider, cwd: string) => `${provider}\0${cwd}`;

function keepForFolder(key: string, commands: ProviderCommand[]) {
  heardByFolder.delete(key);
  heardByFolder.set(key, commands);
  const [oldest] = heardByFolder.keys();
  if (heardByFolder.size > FOLDERS_KEPT) heardByFolder.delete(oldest!);
}

/**
 * Keeps the commands an `available_commands_update` lists. One named `$name`
 * is a skill, as T3 Code reads ACP agents' lists; the first of a name counts.
 * A `config_option_update` refreshes the session's settings, so models that
 * arrive after the session opens are available to the catalog.
 */
export function hearCommands(
  provider: AcpProvider,
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
  heard.set(provider, commands);
  if (cwd) keepForFolder(folderKey(provider, cwd), commands);
}

export const heardCommands = (provider: AcpProvider) => heard.get(provider);

export const heardInFolder = (provider: AcpProvider, cwd: string) =>
  heardByFolder.get(folderKey(provider, cwd));
