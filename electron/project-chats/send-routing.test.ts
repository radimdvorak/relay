import { expect, it } from "vitest";
import type { ProjectChatSend } from "../../shared/projects";
import { withThreadAdvisor } from "./send-routing";

const sent = (body: string, advisor?: string): ProjectChatSend => ({
  id: crypto.randomUUID(),
  body,
  provider: "claude",
  choice: { model: "", reasoningEffort: "", fast: false },
  runtimeMode: "auto",
  interactionMode: "default",
  ...(advisor ? { advisor } : {}),
});

it("keeps the thread's advisor for a message to Claude that names none, as a phone's", () => {
  const last = sent("@claude plan it", "opus");
  expect(withThreadAdvisor(sent("@claude go"), last).advisor).toBe("opus");
  expect(withThreadAdvisor(sent("@claude go", "off"), last).advisor).toBe(
    "off",
  );
  // Another agent's message doesn't carry Claude's.
  const toCodex = { ...sent("@codex go"), provider: "codex" as const };
  expect(withThreadAdvisor(toCodex, last)).not.toHaveProperty("advisor");
});
