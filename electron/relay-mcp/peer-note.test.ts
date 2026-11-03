import { expect, it } from "vitest";
import type { ProjectChat, ProjectChatSend } from "../../shared/projects";
import { peerNote } from "./peer-note";

const chat = {
  id: "forest",
  startedBy: { chatId: "lead", agent: "claude" },
} as ProjectChat;

const from = (fromThread: ProjectChatSend["fromThread"]) =>
  ({ id: "m1", body: "Editor's mine for 2min", fromThread }) as ProjectChatSend;

it("tells the agent which thread wrote and how to answer it", () => {
  const note = peerNote(
    { ...chat, startedBy: undefined },
    from({ id: "damage", name: "Claude in Damage" }),
    true,
  );
  expect(note).toContain("not the user: Claude in Damage (thread damage)");
  expect(note).toContain("send_to_thread with id damage");
});

it("says the user has to pass it on when the agent can't send", () => {
  const note = peerNote(chat, from({ id: "damage", name: "Damage" }), false);
  expect(note).not.toContain("send_to_thread");
  expect(note).toContain("the user can pass it on");
});

it("leaves the user, the lead, started threads, wake-ups and reviewers alone", () => {
  expect(peerNote(chat, from(undefined), true)).toBeUndefined();
  expect(
    peerNote(chat, from({ id: "lead", name: "Lead" }), true),
  ).toBeUndefined();
  expect(
    peerNote(chat, from({ id: "kid", name: "Kid", child: "finished" }), true),
  ).toBeUndefined();
  expect(
    peerNote(chat, from({ id: "forest", name: "Wake-up" }), true),
  ).toBeUndefined();
  const reviewer = { ...chat, reviewer: {} } as ProjectChat;
  expect(
    peerNote(reviewer, from({ id: "review", name: "Deep review" }), true),
  ).toBeUndefined();
});
