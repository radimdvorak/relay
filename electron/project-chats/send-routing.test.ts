import { expect, it } from "vitest";
import type { ThreadAdvisor } from "../../shared/advisor";
import type { ProjectChatSend } from "../../shared/projects";
import { withThreadAdvisor } from "./send-routing";

const sent = (body: string, advisor?: ThreadAdvisor): ProjectChatSend => ({
  id: crypto.randomUUID(),
  body,
  provider: "claude",
  choice: { model: "", reasoningEffort: "", fast: false },
  runtimeMode: "auto",
  interactionMode: "default",
  ...(advisor ? { advisor } : {}),
});

const fable = { provider: "claude" as const, model: "fable" };

it("keeps the thread's advisor for a message to its agent that names none, as a phone's", () => {
  const last = sent("@claude plan it", fable);
  expect(withThreadAdvisor(sent("@claude go"), last).advisor).toEqual(fable);
  expect(withThreadAdvisor(sent("@claude go", "off"), last).advisor).toBe(
    "off",
  );
  // Another agent's message doesn't carry Claude's, nor a note to people.
  const toCodex = { ...sent("@codex go"), provider: "codex" as const };
  expect(withThreadAdvisor(toCodex, last)).not.toHaveProperty("advisor");
  const note = { ...sent("remember this"), to: "message" as const };
  expect(withThreadAdvisor(note, last)).not.toHaveProperty("advisor");
});
