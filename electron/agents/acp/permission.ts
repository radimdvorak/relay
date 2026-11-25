// Answering an ACP agent's ask to run a tool, from Relay's mode or the user.
import { resolve } from "node:path";
import type { AgentDecision } from "../../../shared/agent-modes";
import { mcpCall } from "../activity";
import { answerAntigravityQuestion, antigravityMcp } from "../antigravity";
import type { AgentOptions } from "../types";
import { editKinds, lookKinds, rawField } from "./activity";
import {
  cancelledPermission,
  permissionRequestSchema,
  picked,
  type AcpPermissionRequest,
} from "./protocol";

const decisionKinds: Record<Exclude<AgentDecision, "cancel">, string> = {
  accept: "allow_once",
  acceptForSession: "allow_always",
  decline: "reject_once",
};

/** The option of `kind` the agent offered, falling back along `kinds`. */
const optionOf = (request: AcpPermissionRequest, ...kinds: string[]) =>
  kinds
    .map((kind) => request.options.find((o) => o.kind === kind))
    .find(Boolean);

/**
 * Whether the user can be offered `decision`. Gemini offers several "always"
 * options (a whole MCP server, one tool, every future session), and Relay's
 * one Always can't say which it would pick, so it offers Always only alone.
 */
function canOffer(request: AcpPermissionRequest, decision: AgentDecision) {
  if (decision === "cancel") return false;
  const kind = decisionKinds[decision];
  const offered = request.options.filter((o) => o.kind === kind).length;
  return decision === "acceptForSession" ? offered === 1 : offered > 0;
}

/** What the user sees of a permission request: the command, or the files it changes. */
function requestDetail(request: AcpPermissionRequest) {
  const command = rawField(
    request.toolCall.rawInput,
    "command",
    "cmd",
    "CommandLine",
  );
  const paths = [
    ...(request.toolCall.content ?? []).flatMap((c) =>
      c.type === "diff" && c.path ? [c.path] : [],
    ),
    ...(request.toolCall.locations ?? []).map((l) => l.path),
  ];
  return command || [...new Set(paths)].join("\n") || undefined;
}

/** Whether the agent is only asking to read audio files the user attached to this turn. */
function readsAttachedAudio(
  options: AgentOptions,
  request: AcpPermissionRequest,
) {
  if (!options.audio?.length || request.toolCall.kind !== "read") return false;
  const attached = new Set(options.audio.map((clip) => resolve(clip.path)));
  const inputPath = rawField(
    request.toolCall.rawInput,
    "Path",
    "path",
    "file_path",
    "filePath",
  );
  const paths = [
    ...(request.toolCall.locations ?? []).map((l) => l.path),
    ...(inputPath ? [inputPath] : []),
  ];
  return paths.length > 0 && paths.every((path) => attached.has(resolve(path)));
}

/** What the approval is called: the MCP tool it's for, or the agent's own title. */
function requestTitle(request: AcpPermissionRequest, isAntigravity: boolean) {
  const mcp = isAntigravity && antigravityMcp(request.toolCall);
  return mcp ? mcpCall(mcp.server, mcp.tool).label : request.toolCall.title;
}

/**
 * Answers the agent's ask to run a tool. Relay's mode answers what it can;
 * the rest goes to the user, and with nobody to ask the tool is refused.
 */
export async function permission(
  options: AgentOptions,
  name: string,
  raw: unknown,
  signal: AbortSignal,
  isAntigravity: boolean,
) {
  const request = permissionRequestSchema.parse(raw);
  if (isAntigravity && request.toolCall.toolCallId?.startsWith("interaction_"))
    return answerAntigravityQuestion(options, request, signal);
  const kind = request.toolCall.kind ?? "other";
  const allow = () => optionOf(request, "allow_once", "allow_always");
  const reject = () => optionOf(request, "reject_once", "reject_always");
  const answer = (option?: { optionId: string }) =>
    option ? picked(option.optionId) : cancelledPermission;
  if (signal.aborted) return cancelledPermission;
  if (
    options.job.kind === "helper" ||
    (isAntigravity && options.job.kind === "compact")
  )
    return answer(reject());
  if (isAntigravity && readsAttachedAudio(options, request))
    return answer(allow());
  if (options.readOnly && !lookKinds.has(kind)) return answer(reject());
  if (options.runtimeMode === "full-access") return answer(allow());
  if (options.runtimeMode === "auto-accept-edits" && editKinds.has(kind))
    return answer(allow());
  if (!options.onRequest || options.readOnly) return answer(reject());

  const decisions = (
    Object.keys(decisionKinds) as (keyof typeof decisionKinds)[]
  ).filter((decision) => canOffer(request, decision));
  const response = await options
    .onRequest(
      {
        kind: "approval",
        title:
          requestTitle(request, isAntigravity) || `${name} wants to run a tool`,
        detail: requestDetail(request),
        decisions,
      },
      signal,
    )
    .catch(() => undefined);
  if (signal.aborted || response?.kind !== "approval")
    return cancelledPermission;
  if (response.decision === "cancel") return answer(reject());
  return answer(
    optionOf(request, decisionKinds[response.decision]) ?? reject(),
  );
}
