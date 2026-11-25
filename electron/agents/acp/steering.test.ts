import { expect, it, vi } from "vitest";
import type { Settled } from "../line-connection";
import { PromptSteering, type KeptRun } from "./steering";

type Heard = (settled: Settled) => void;

/** A prompt's request, which the test answers; it's heard first, as a connection has it. */
function request(heard: Heard) {
  let settle!: Heard;
  const reply = new Promise<unknown>((resolve, reject) => {
    settle = ({ result, error }) => (error ? reject(error) : resolve(result));
  });
  const answer = (settled: Settled) => {
    heard(settled);
    settle(settled);
  };
  return {
    reply,
    resolve: (result: unknown) => answer({ result }),
    reject: (error: Error) => answer({ error }),
  };
}

/** A turn whose first prompt runs, and whose steers answer as the test says. */
function turn() {
  const prompts: ReturnType<typeof request>[] = [];
  const state = { waiting: false, over: false };
  const ask = (heard: Heard) => prompts[prompts.push(request(heard)) - 1].reply;
  let lastRequest = 0;
  const io = {
    reserve: () => ++lastRequest,
    send: vi.fn((_request: number, _prompt: object[], heard: Heard) =>
      ask(heard),
    ),
    wait: (_request: number, heard: Heard) => ask(heard),
    keep: vi.fn<(run: KeptRun) => void>(),
    images: async (): Promise<object[]> => [],
    over: () => state.over,
    waiting: () => state.waiting,
    steered: vi.fn(),
    unread: vi.fn(),
    carryOn: () => undefined,
  };
  const steering = new PromptSteering(io);
  steering.start([{ type: "text", text: "Do A" }]);
  io.send.mockClear();
  io.keep.mockClear();
  const steer = (at: number) => ({
    resolve: (result: unknown) => prompts[at + 1].resolve(result),
    reject: (error: Error) => prompts[at + 1].reject(error),
  });
  const interim = vi.fn();
  return {
    first: prompts[0],
    steers: [steer(0), steer(1)],
    state,
    io,
    steering,
    interim,
  };
}

const ended = { stopReason: "end_turn" };

it("goes on with the steer's prompt once the agent lets the first one go", async () => {
  const { first, steers, io, steering, interim } = turn();
  const result = steering.result([], interim);
  await steering.steer("Also do B", "m1");
  expect(io.send).toHaveBeenCalledWith(
    2,
    [{ type: "text", text: "Also do B" }],
    expect.any(Function),
  );
  expect(io.steered).not.toHaveBeenCalled();

  first.resolve({ ...ended, usage: { outputTokens: 1 } });
  await vi.waitFor(() => expect(io.steered).toHaveBeenCalledWith("m1"));
  steers[0].resolve({ ...ended, usage: { outputTokens: 2 } });

  expect(await result).toMatchObject({ usage: { outputTokens: 2 } });
  expect(interim.mock.calls[0][0]).toMatchObject({
    usage: { outputTokens: 1 },
  });
  expect(io.unread).not.toHaveBeenCalled();
});

it("takes several steers in order, and the last one's answer is the turn's", async () => {
  const { first, steers, io, steering, interim } = turn();
  const result = steering.result([], interim);
  await steering.steer("B", "m1");
  await steering.steer("C", "m2");
  first.resolve(ended);
  steers[0].resolve(ended);
  steers[1].resolve({ stopReason: "max_tokens" });
  expect(await result).toMatchObject({ stopReason: "max_tokens" });
  expect(io.steered.mock.calls).toEqual([["m1"], ["m2"]]);
});

it("keeps the turn going when the agent refuses a steer, and says it wasn't read", async () => {
  const { first, steers, io, steering, interim } = turn();
  const result = steering.result([], interim);
  await steering.steer("B", "m1");
  const refusal = new Error("Can't add this prompt");
  steers[0].reject(refusal);
  await vi.waitFor(() => expect(io.unread).toHaveBeenCalledWith("m1", refusal));
  // A restarted Relay must wait on the prompt still running.
  expect(io.keep.mock.lastCall![0]).toEqual({
    id: 1,
    prompts: [{ request: 1 }],
  });
  first.resolve({ ...ended, usage: { outputTokens: 5 } });
  expect(await result).toMatchObject({ usage: { outputTokens: 5 } });
  expect(io.steered).not.toHaveBeenCalled();
});

it("still hands over to the next steer when the one before it was refused", async () => {
  const { first, steers, io, steering, interim } = turn();
  const result = steering.result([], interim);
  await steering.steer("B", "m1");
  await steering.steer("C", "m2");
  steers[0].reject(new Error("Can't add this prompt"));
  await vi.waitFor(() => expect(io.unread).toHaveBeenCalledTimes(1));
  first.resolve(ended);
  await vi.waitFor(() => expect(io.steered).toHaveBeenCalledWith("m2"));
  steers[1].resolve({ stopReason: "max_tokens" });
  expect(await result).toMatchObject({ stopReason: "max_tokens" });
});

it("leaves what a restarted Relay waits on alone once the turn has ended", async () => {
  const { first, steers, io, steering, interim } = turn();
  let stop!: (error: Error) => void;
  const stopped = new Promise<never>((_, reject) => (stop = reject));
  const result = steering.result([stopped], interim);
  await steering.steer("B", "m1");
  stop(new Error("Cancelled by you."));
  await expect(result).rejects.toThrow("Cancelled");
  io.keep.mockClear();
  steers[0].reject(new Error("Can't add this prompt"));
  await vi.waitFor(() => expect(io.unread).toHaveBeenCalled());
  expect(io.keep).not.toHaveBeenCalled();
  first.resolve(ended);
});

it("fails the turn when the agent fails on a steer it took up", async () => {
  const { first, steers, io, steering, interim } = turn();
  const result = steering.result([], interim);
  await steering.steer("B", "m1");
  first.resolve(ended);
  await vi.waitFor(() => expect(io.steered).toHaveBeenCalled());
  steers[0].reject(new Error("502"));
  await expect(result).rejects.toThrow("502");
  expect(io.unread).not.toHaveBeenCalled();
});

it("sends nothing while the agent waits for an answer, or once the turn is over", async () => {
  const { first, state, io, steering, interim } = turn();
  const result = steering.result([], interim);
  state.waiting = true;
  await expect(steering.steer("B", "m1")).rejects.toThrow(
    /waiting for your answer/,
  );
  state.waiting = false;
  first.resolve(ended);
  await result;
  await expect(steering.steer("B", "m1")).rejects.toThrow(/turn has finished/);
  expect(io.send).not.toHaveBeenCalled();
});

it("turns away a steer whose preparation finishes right after the active prompt already answered", async () => {
  const { first, io, steering, interim } = turn();
  const preparing = Promise.withResolvers<object[]>();
  io.images = () => preparing.promise;
  const result = steering.result([], interim);

  const late = steering.steer("B", "m1");
  first.resolve(ended);
  preparing.resolve([]);

  await expect(late).rejects.toThrow(/turn has finished/);
  expect(await result).toEqual(ended);
  expect(io.send).not.toHaveBeenCalled();
  expect(io.steered).not.toHaveBeenCalled();
});
