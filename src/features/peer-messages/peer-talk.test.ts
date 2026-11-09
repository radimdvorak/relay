import { describe, expect, it } from "vitest";
import type { PeerMessage } from "../../../shared/peer-messages";
import { currentTalk, talkState } from "./peer-talk";

const minute = 60_000;
const exhaust = { name: "mudtoy-d4", threadId: "exhaust" };
const editorAsk = { name: "mudtoy-7c", threadId: "roads" };

const message = (
  id: string,
  direction: PeerMessage["direction"],
  at: number,
  peer = exhaust,
): PeerMessage => ({ id, direction, peer, text: id, at });

describe("currentTalk", () => {
  it("shows the exchange with whoever the last message was with", () => {
    const talk = currentTalk(
      [
        message("ask", "out", 0, editorAsk),
        message("reply", "in", 1, editorAsk),
        message("can I have Play?", "out", 2),
        message("in 3min", "in", 3),
      ],
      4,
    );
    expect(talk?.peer).toBe(exhaust);
    expect(talk?.messages.map((m) => m.id)).toEqual([
      "can I have Play?",
      "in 3min",
    ]);
  });

  it("leaves out a broadcast the thread never answered", () => {
    expect(
      currentTalk([message("anyone using the editor?", "in", 0)], 1),
    ).toBeUndefined();
  });

  it("puts an exchange away half an hour after its last message", () => {
    const talk = [message("a", "out", 0), message("b", "in", minute)];
    expect(currentTalk(talk, 30 * minute)).toBeDefined();
    expect(currentTalk(talk, 32 * minute)).toBeUndefined();
  });
});

describe("talkState", () => {
  const talk = currentTalk(
    [message("can I have Play?", "out", 0), message("in 3min", "in", minute)],
    minute,
  )!;
  const idle = (updated: number) => ({ updated });

  it("says who's working before anything else", () => {
    const now = 10 * minute;
    expect(
      talkState(talk, { running: true, updated: 0 }, idle(0), false, now),
    ).toEqual({ kind: "working", who: "this" });
    expect(
      talkState(talk, idle(0), { running: true, updated: 0 }, false, now),
    ).toEqual({ kind: "working", who: "peer" });
  });

  it("calls it stuck when both stopped right after talking and stayed quiet", () => {
    const state = talkState(
      talk,
      idle(minute + 20_000),
      idle(minute + 30_000),
      false,
      5 * minute,
    );
    expect(state).toEqual({ kind: "stalled", since: minute + 30_000 });
  });

  it("isn't stuck yet in the first couple of minutes", () => {
    expect(
      talkState(talk, idle(minute), idle(minute), false, 2 * minute).kind,
    ).toBe("quiet");
  });

  it("isn't stuck when one kept working well past the exchange", () => {
    expect(
      talkState(talk, idle(minute), idle(9 * minute), false, 20 * minute).kind,
    ).toBe("quiet");
  });

  it("leaves it to the user once they wrote, or when the other session isn't a thread", () => {
    expect(
      talkState(talk, idle(minute), idle(minute), true, 10 * minute).kind,
    ).toBe("quiet");
    expect(
      talkState(talk, idle(minute), undefined, false, 10 * minute).kind,
    ).toBe("quiet");
  });
});
