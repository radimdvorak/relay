import { expect, it } from "vitest";
import type { ThreadAdvisor } from "../../shared/advisor";
import type {
  ChatMessage,
  ProjectChat,
  ProjectChatSend,
} from "../../shared/projects";
import { sentSettingsOf } from "../../shared/projects";
import { withThreadAdvisor } from "./send-advisor";

const sent = (body: string, advisor?: ThreadAdvisor): ProjectChatSend => ({
  id: crypto.randomUUID(),
  body,
  provider: "claude",
  choice: { model: "", reasoningEffort: "", fast: false },
  runtimeMode: "auto",
  interactionMode: "default",
  ...(advisor ? { advisor } : {}),
});

/** A thread whose user messages were `sends`, the last one its lastInput. */
const thread = (...sends: ProjectChatSend[]): ProjectChat =>
  ({
    messages: sends.map((s): Partial<ChatMessage> => ({
      id: s.id,
      role: "user",
      body: s.body,
      ...(s.to === "message" ? {} : { sentSettings: sentSettingsOf(s) }),
    })),
    lastInput: sends.at(-1),
  }) as ProjectChat;

const fable = { provider: "claude" as const, model: "fable" };

it("keeps the thread's advisor for a message to its agent that names none, as a phone's", () => {
  const advised = thread(sent("@claude plan it", fable));
  expect(withThreadAdvisor(sent("@claude go"), advised).advisor).toEqual(fable);
  expect(withThreadAdvisor(sent("@claude go", "off"), advised).advisor).toBe(
    "off",
  );
  // Another agent's message doesn't carry Claude's, nor a note to people.
  const toCodex = { ...sent("@codex go"), provider: "codex" as const };
  expect(withThreadAdvisor(toCodex, advised)).not.toHaveProperty("advisor");
  const note = { ...sent("remember this"), to: "message" as const };
  expect(withThreadAdvisor(note, advised)).not.toHaveProperty("advisor");
});

it("keeps it past a note to people and another agent's turn", () => {
  const note = { ...sent("remember this"), provider: "codex" as const };
  const toCodex = { ...sent("@codex check"), provider: "codex" as const };
  const advised = thread(
    sent("@claude plan it", fable),
    { ...note, to: "message" },
    toCodex,
  );
  expect(withThreadAdvisor(sent("@claude go"), advised).advisor).toEqual(fable);
});
