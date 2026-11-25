import { expect, it, vi } from "vitest";
import { HostedChild } from "../../agent-host/child";
import type { HostedProcess } from "../../agent-host/client";
import type { Entry } from "../../agent-host/protocol";
import { replayLines } from "../hosted-replay";
import { TurnText } from "../turn-kit";
import { AcpConnection } from "./connection";
import { PromptSteering, type KeptRun } from "./steering";

const line = (message: object) =>
  JSON.stringify({ jsonrpc: "2.0", ...message });

const said = (text: string) =>
  line({
    method: "session/update",
    params: { sessionId: "s", update: text },
  });

const answered = (id: number) =>
  line({ id, result: { stopReason: "end_turn" } });

type Handlers = Parameters<HostedProcess["read"]>[0];

/** One Relay's end of a hosted agent, which `log` was said to before it connected. */
function relay(log: string[] = [], after?: number) {
  let host!: Handlers;
  const hosted = {
    write() {},
    read: (handlers: Handlers) => (host = handlers),
  } as unknown as HostedProcess;
  const child = new HostedChild(hosted, (entries) =>
    replayLines(entries, 0, after),
  );
  const connection = new AcpConnection(child, "Agent");
  connection.continueAfter(log.length ? 100 : 0);
  const entry = (text: string, seq: number): Entry => ({
    kind: "line",
    text,
    seq,
  });
  host.replayed?.(log.map(entry));

  let answer = "";
  const steered: string[] = [];
  const turn = new TurnText((text) => (answer = text));
  connection.listen("s", (text) => turn.add(text as string));
  let kept!: KeptRun;
  const steering = new PromptSteering({
    reserve: () => connection.reserve(),
    send: (id, _prompt, heard) =>
      connection.request("session/prompt", {}, id, heard),
    wait: (id, heard) => connection.wait(id, "session/prompt", heard),
    keep: (run) => (kept = run),
    images: async () => [],
    over: () => false,
    waiting: () => false,
    steered: (id) => {
      turn.below();
      steered.push(id);
    },
    unread: () => {},
    carryOn: () => undefined,
  });
  return {
    connection,
    steering,
    steered,
    answer: () => answer,
    kept: () => kept,
    /** What the agent says, as one batch. */
    says: (...lines: string[]) =>
      lines.forEach((text, at) => host.entry(entry(text, log.length + at))),
  };
}

it("starts the steered answer below when it arrives with the reply before it", async () => {
  const { connection, steering, steered, answer, says } = relay();
  connection.resume();
  steering.start([]);
  const result = steering.result([], () => {});
  await steering.steer("B", "m1");
  says(said("Old. "), answered(1), said("New "), said("answer."), answered(2));
  await result;
  expect(steered).toEqual(["m1"]);
  expect(answer()).toBe("New answer.");
});

it("shows only the steered answer again when Relay restarts after the steer was taken up", async () => {
  const before = relay();
  before.connection.resume();
  before.steering.start([]);
  await before.steering.steer("B", "m1");
  const log = [said("Old. "), answered(1), said("New ")];
  before.says(...log);
  await vi.waitFor(() => expect(before.steered).toEqual(["m1"]));

  const run = before.kept();
  const restarted = relay(log, run.takenUp?.after);
  restarted.steering.pickUp(run);
  const result = restarted.steering.result([], () => {});
  restarted.connection.resume();
  restarted.says(said("answer."), answered(2));
  await result;
  expect(restarted.steered).toEqual(["m1"]);
  expect(restarted.answer()).toBe("New answer.");
});

it("takes the steer up in the replay when the agent read it while Relay was away", async () => {
  const before = relay();
  before.connection.resume();
  before.steering.start([]);
  await before.steering.steer("B", "m1");
  const run = before.kept();

  const texts = [said("Old. "), answered(1), said("New answer."), answered(2)];
  const restarted = relay(texts, run.takenUp?.after);
  restarted.steering.pickUp(run);
  const interim = vi.fn();
  const result = restarted.steering.result([], interim);
  restarted.connection.resume();
  await result;
  expect(restarted.steered).toEqual(["m1"]);
  expect(restarted.answer()).toBe("New answer.");
  expect(interim).toHaveBeenCalledOnce();
});

it("leaves the turn running when the agent refused the steer while Relay was away", async () => {
  const before = relay();
  before.connection.resume();
  before.steering.start([]);
  await before.steering.steer("B", "m1");
  const run = before.kept();

  const refused = line({ id: 2, error: { code: -32600, message: "Busy" } });
  const restarted = relay([said("All "), refused], run.takenUp?.after);
  restarted.steering.pickUp(run);
  const result = restarted.steering.result([], () => {});
  restarted.connection.resume();
  restarted.says(said("done."), answered(1));
  expect(await result).toMatchObject({ stopReason: "end_turn" });
  expect(restarted.steered).toEqual([]);
  expect(restarted.answer()).toBe("All done.");
});
