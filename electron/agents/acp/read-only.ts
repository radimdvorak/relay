// What a read-only turn may do on an ACP agent, and how it goes on once Relay
// has turned a call down: some agents end the whole turn over one refusal.
import { readsOnly } from "../../agent-host/read-only-bash";
import { antigravityMcp, updatesSteps } from "../antigravity";
import type { AgentOptions } from "../types";
import { lookKinds, rawField } from "./activity";
import type { AcpPermissionRequest, AcpPromptResult } from "./protocol";

/** Whether the call is a shell command Relay knows only reads. */
function onlyReadsInShell({ toolCall }: AcpPermissionRequest) {
  // The list is read as bash and zsh would; Windows agents run PowerShell.
  if (process.platform === "win32") return false;
  const { rawInput } = toolCall;
  const command = rawField(rawInput, "CommandLine", "command", "cmd");
  return !!command && readsOnly(command);
}

/** Whether a read-only turn may make the call its agent asks about. */
export function readOnlyAllows(
  options: Pick<AgentOptions, "runtimeMode">,
  request: AcpPermissionRequest,
  isAntigravity: boolean,
) {
  const kind = request.toolCall.kind ?? "other";
  // Whatever kind an MCP tool calls itself, only its server knows what it does.
  if (isAntigravity && antigravityMcp(request.toolCall)) return false;
  // Its own task list; turning it down ends Antigravity's whole turn.
  if (isAntigravity && updatesSteps(request.toolCall)) return true;
  if (kind === "execute") return onlyReadsInShell(request);
  // A fetch can carry what was read off this computer.
  if (kind === "fetch") return options.runtimeMode === "full-access";
  return lookKinds.has(kind);
}

const carryOnPrompt =
  "Relay turned that call down because this thread only reads, and your turn ended there. The task still stands: carry on with it and finish it. Don't retry the refused call or look for a way around it. Use your file reading and search tools, or a plain command that only reads, like git diff, git log, git show, git status, rg, grep, cat, head or sed -n, with no redirects, command substitution, subshells or scripts. End with the complete written answer.";

/** The turn kept ending over calls Relay turned down, however often it was told to carry on. */
export const gaveUpOnRefusals = (name: string) =>
  new Error(
    `${name} stopped without an answer: it kept asking for things a read-only thread doesn't allow.`,
  );

/** How often one turn is told to carry on, and how often in a row without a call going through. */
const MAX_CARRY_ONS = 10;
const MAX_WITHOUT_PROGRESS = 2;

/** The calls Relay turned down in a read-only turn, and whether the turn goes on after them. */
export class ReadOnlyRefusals {
  private refused = false;
  private carryOns = 0;
  private withoutProgress = 0;
  private completedBefore = 0;

  add = () => {
    this.refused = true;
  };

  /** The turn ended over a refused call, with no answer written. */
  endedBy({ stopReason }: AcpPromptResult, answer: string) {
    const agentEnded = stopReason === "end_turn" || stopReason === "cancelled";
    return this.refused && agentEnded && !answer.trim();
  }

  /**
   * What to tell an agent whose turn ended over a refused call, or undefined
   * once it has been told enough. `completed` is how many calls went through.
   */
  carryOn(completed: number): string | undefined {
    const progressed = completed > this.completedBefore;
    if (progressed) this.withoutProgress = 0;
    const toldEnough =
      this.carryOns >= MAX_CARRY_ONS ||
      this.withoutProgress >= MAX_WITHOUT_PROGRESS;
    if (toldEnough) return undefined;
    this.refused = false;
    this.completedBefore = completed;
    this.carryOns++;
    this.withoutProgress++;
    return carryOnPrompt;
  }
}
