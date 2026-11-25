import { expect, it, vi } from "vitest";
import { PromptSteering } from "./steering";

function deferred() {
  let resolve!: (value: unknown) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<unknown>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

/** A turn whose first prompt runs, and whose steers answer as the test says. */
function turn() {
  const first = deferred();
  const steers: ReturnType<typeof deferred>[] = [];
  const state = { waiting: false, over: false };
  const io = {
    send: vi.fn((_prompt: object[]) => {
      const steer = deferred();
      steers.push(steer);
      return { request: steers.length + 1, reply: steer.promise };
    }),
    waitOn: vi.fn(),
    images: async (): Promise<object[]> => [],
    over: () => state.over,
    waiting: () => state.waiting,
    steered: vi.fn(),
    unread: vi.fn(),
  };
  const steering = new PromptSteering({ request: 1, reply: first.promise }, io);
  const interim = vi.fn();
  return { first, steers, state, io, steering, interim };
}

const ended = { stopReason: "end_turn" };

it("goes on with the steer's prompt once the agent lets the first one go", async () => {
  const { first, steers, io, steering, interim } = turn();
  const result = steering.result([], interim);
  await steering.steer("Also do B", "m1");
  expect(io.send).toHaveBeenCalledWith([{ type: "text", text: "Also do B" }]);
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
  expect(io.waitOn).toHaveBeenCalledExactlyOnceWith(1);
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
  steers[0].reject(new Error("Can't add this prompt"));
  await vi.waitFor(() => expect(io.unread).toHaveBeenCalled());
  expect(io.waitOn).not.toHaveBeenCalled();
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
  const preparing = deferred();
  io.images = () => preparing.promise as Promise<object[]>;
  const result = steering.result([], interim);

  const late = steering.steer("B", "m1");
  first.resolve(ended);
  preparing.resolve([]);

  await expect(late).rejects.toThrow(/turn has finished/);
  expect(await result).toEqual(ended);
  expect(io.send).not.toHaveBeenCalled();
  expect(io.steered).not.toHaveBeenCalled();
});
