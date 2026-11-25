import { agentName } from "../../../shared/agents";
import { signedOutError } from "../errors";
import { cancelGrace, TurnText } from "../turn-kit";
import type { AgentOptions } from "../types";
import type { Settled } from "../line-connection";
import { AgentRequests } from "./agent-requests";
import { AcpRequestError } from "./connection";
import type { AcpProfile } from "./profiles";
import { AUTH_REQUIRED, type AcpPromptResult } from "./protocol";
import { imageBlocks, mcpServers, promptBlocks } from "./prompt";
import { acquireTurnAgent, openSession, type AcpAgent } from "./sessions";
import { applySettings } from "./settings";
import { PromptSteering, type KeptRun } from "./steering";
import { antigravityHelper, type TextSpeed } from "../antigravity";
import { AcpTurnEvents } from "./turn-events";

export type AcpRunObserver = {
  /** Drains native events before the ACP turn settles, including steer acknowledgements. */
  settle(): Promise<void>;
  failure?: Promise<never>;
};

/** `steered` acknowledges a steer the native API saw read, so its answer goes below it. */
type Observe = (
  agent: AcpAgent,
  steered: (id: string) => void,
) => Promise<AcpRunObserver>;

/**
 * Runs one turn on an ACP agent, starting or picking up its session in the
 * thread's process. Native APIs may augment ACP through `observe` without
 * replacing its per-session MCP and approvals.
 */
export async function runAcp(
  profile: AcpProfile,
  options: AgentOptions,
  observe?: Observe,
): Promise<string> {
  if (profile.provider !== "antigravity" || options.job.kind !== "helper")
    return runAcpTurn(profile, options, observe);
  options.signal.throwIfAborted();
  const helper = await antigravityHelper(profile.home);
  const deadline = new AbortController();
  const timer = setTimeout(() => deadline.abort(), 180_000);
  let sessionId: string | undefined;
  try {
    const answer = await runAcpTurn(profile, {
      ...options,
      cwd: helper.cwd,
      signal: AbortSignal.any([options.signal, deadline.signal]),
      session: {
        onId: async (id) => {
          sessionId = id;
        },
      },
      relayTools: undefined,
      readOnly: true,
    });
    await helper.check();
    return answer;
  } catch (error) {
    if (deadline.signal.aborted && !options.signal.aborted)
      throw new Error("Antigravity reached the three-minute helper limit.");
    throw error;
  } finally {
    clearTimeout(timer);
    await helper.close(sessionId);
  }
}

/** Opens the thread's session, or a new one, in the turn's modes and model; returns its id. */
async function openTurnSession(
  profile: AcpProfile,
  options: AgentOptions,
  agent: AcpAgent,
  name: string,
) {
  const { job } = options;
  const opened = await openSession(agent, {
    id: job.kind === "helper" ? undefined : options.session?.id,
    cwd: options.cwd,
    mcpServers: mcpServers(options, agent),
  });
  if (opened.id !== options.session?.id) await options.session?.onId(opened.id);
  if (opened.lost)
    options.onCommentary?.(
      `${profile.provider}-lost`,
      `${name} couldn't open the earlier conversation, so this turn starts a new one.`,
    );
  await applySettings(
    agent.connection,
    agent.session!,
    profile.wishes({
      runtime: options.runtimeMode,
      interaction: options.interactionMode,
      readOnly: options.readOnly || job.kind === "helper",
    }),
    options.choice,
  );
  return opened.id;
}

/** How a turn's prompts reach its session, and the next Relay. */
const promptRequests = (agent: AcpAgent, sessionId: () => string) => ({
  reserve: () => agent.connection.reserve(),
  send: (request: number, prompt: object[], heard: (s: Settled) => void) =>
    agent.connection.request(
      "session/prompt",
      { sessionId: sessionId(), prompt },
      request,
      heard,
    ),
  wait: (request: number, heard: (s: Settled) => void) =>
    agent.connection.wait(request, "session/prompt", heard),
  keep: (run: KeptRun) => agent.keep(run),
});

async function runAcpTurn(
  profile: AcpProfile,
  options: AgentOptions,
  observe?: Observe,
): Promise<string> {
  const { signal, job } = options;
  const name = agentName(profile.provider);
  signal.throwIfAborted();
  if (job.kind === "compact" && !profile.compact)
    throw new Error(
      `${name} summarizes a long thread on its own; there is nothing to compact by hand.`,
    );
  if (job.kind === "side" || options.session?.fork)
    throw new Error(
      `${name} can't copy a conversation, so it can't answer beside the thread.`,
    );
  if (job.kind === "review" || job.kind === "goal")
    throw new Error(`${name} has no /${job.kind}.`);

  // Started signed out, Antigravity would open its sign-in page unasked.
  if (job.kind !== "adopt" && (await profile.account?.())?.signedIn === false)
    throw signedOutError(profile.provider);

  // A helper job is one bare question in a process of its own, never the thread's.
  const key = job.kind === "helper" ? undefined : options.session?.key;
  const agent = await acquireTurnAgent(profile, options, key);
  const { connection } = agent;
  const adopted = job.kind === "adopt" ? agent.inflight : undefined;
  if (job.kind === "adopt" && (!adopted || !agent.session)) {
    agent.busy = false;
    throw new Error(`There is no ${name} turn to pick up.`);
  }

  let overflow!: (error: Error) => void;
  const overflowed = new Promise<never>((_, reject) => (overflow = reject));
  void overflowed.catch(() => {});
  const turnAbort = new AbortController();
  const stop = () => turnAbort.abort();
  signal.addEventListener("abort", stop, { once: true });
  const events = new AcpTurnEvents(
    profile,
    options,
    agent,
    key,
    (error) => {
      overflow(error);
      stop();
    },
    () => turnAbort.signal.aborted,
  );
  const { turn, calls, subagents, speed, hear } = events;
  const steered = (id: string) => {
    turn.below();
    options.onSteered?.(id);
  };

  let sessionId = agent.session?.id;
  const cancel = () => {
    if (sessionId) connection.notify("session/cancel", { sessionId });
  };
  turnAbort.signal.addEventListener("abort", cancel, { once: true });

  const requests = new AgentRequests(
    profile,
    name,
    options,
    events.asking,
    turnAbort.signal,
  );
  connection.onRequest = requests.answer;
  const steering = new PromptSteering({
    ...promptRequests(agent, () => sessionId!),
    images: (images) => imageBlocks(images, agent, name),
    over: () => turnAbort.signal.aborted,
    waiting: () => requests.waiting,
    steered,
    unread: (id, error) => options.onSteerUnread?.(id, error),
  });

  let observer: AcpRunObserver | undefined;
  let grace: ReturnType<typeof cancelGrace> | undefined;
  try {
    connection.mark("start");
    if (adopted) {
      connection.listen(sessionId!, hear);
      steering.pickUp(adopted);
      try {
        observer = await observe?.(agent, steered);
      } finally {
        // What it said while Relay was away comes now, with a listener in
        // place. Held back after a failed start, the process would answer
        // nothing ever again, this turn or the next.
        connection.resume();
      }
    } else {
      sessionId = await openTurnSession(profile, options, agent, name);
      const prompt = await promptBlocks(options, agent, name, profile);
      signal.throwIfAborted();
      connection.listen(sessionId, hear);
      observer = await observe?.(agent, steered);
      signal.throwIfAborted();
      steering.start(prompt);
    }
    // The agent reports the cancel; if it doesn't, stop waiting anyway.
    grace = cancelGrace(turnAbort.signal);
    if (signal.aborted) stop();
    if (profile.steersByPrompt && (job.kind === "prompt" || adopted))
      options.onControl?.({ steer: steering.steer });
    const result = await steering.result(
      [grace.cancelled, overflowed, observer?.failure],
      (earlier) => reportUsage(options, profile, earlier, speed),
    );
    await observer?.settle();
    finish(options, profile, result, turn, name, speed);
    const answer = turn.answer();
    const failed = profile.failure?.(answer, calls.size > 0);
    if (failed) {
      options.onText("");
      throw new Error(`${name}: ${failed}`);
    }
    if (options.interactionMode === "plan" && answer.trim())
      options.onPlan?.(answer);
    return answer;
  } catch (error) {
    if (observer) {
      cancel();
      await observer.settle().catch(() => {});
    }
    if (error instanceof AcpRequestError && error.code === AUTH_REQUIRED) {
      // Its process owns cached credentials. A new login must get a fresh process.
      agent.close();
      throw signedOutError(
        profile.provider,
        profile.provider === "opencode"
          ? "OpenCode's model provider needs you to sign in again. Reconnect the provider, then resume the answer."
          : undefined,
      );
    }
    throw error;
  } finally {
    subagents?.settle(signal.aborted || turnAbort.signal.aborted);
    grace?.clear();
    signal.removeEventListener("abort", stop);
    // Answers whatever the agent still asks of a turn that's over, without cancelling it.
    turnAbort.signal.removeEventListener("abort", cancel);
    turnAbort.abort();
    if (sessionId) connection.unlisten(sessionId);
    connection.onRequest = undefined;
    // What the last Relay's cancels were answered with, in the replay.
    if (adopted) connection.dropOrphans();
    agent.settle(!!key);
  }
}

type AcpUsage = NonNullable<AcpPromptResult["usage"]>;

/**
 * ACP leaves open whether `outputTokens` counts thinking. Antigravity 1.5's
 * does, so adding `thoughtTokens` counted it twice; the total says which,
 * whether or not `inputTokens` counts what came from the cache.
 */
export function outputTokens(usage: AcpUsage) {
  const output = usage.outputTokens ?? 0;
  const thoughts = usage.thoughtTokens ?? 0;
  const input = usage.inputTokens ?? 0;
  const cached = (usage.cachedReadTokens ?? 0) + (usage.cachedWriteTokens ?? 0);
  const countsThoughts = [input + output, input + cached + output].includes(
    usage.totalTokens ?? NaN,
  );
  return countsThoughts ? output : output + thoughts;
}

/** What one of the turn's prompts used; a steered turn has several. */
function reportUsage(
  options: AgentOptions,
  profile: AcpProfile,
  { usage }: AcpPromptResult,
  speed?: TextSpeed,
) {
  if (!usage) return;
  const output = outputTokens(usage);
  const streamed = speed?.finish(output - (usage.thoughtTokens ?? 0));
  options.onUsage?.({
    model: options.choice.model || profile.provider,
    tokens: {
      input: usage.inputTokens ?? 0,
      cacheWrite: usage.cachedWriteTokens ?? 0,
      cacheRead: usage.cachedReadTokens ?? 0,
      output,
    },
    ...(streamed && { streamed }),
  });
}

/** Reads how the turn ended: a reason to fail it, and what it used. */
function finish(
  options: AgentOptions,
  profile: AcpProfile,
  result: AcpPromptResult,
  turn: TurnText,
  name: string,
  speed?: TextSpeed,
) {
  reportUsage(options, profile, result, speed);
  switch (result.stopReason) {
    case "cancelled":
      throw new Error("Cancelled by you.");
    case "refusal":
      throw new Error(`${name} refused to go on.`);
    case "max_tokens":
      if (!turn.answer())
        throw new Error(`${name} ran out of tokens before it answered.`);
      return;
    case "max_turn_requests":
      if (!turn.answer())
        throw new Error(`${name} reached its limit of steps for one turn.`);
  }
}
