import { expect, it } from "vitest";
import { advisorRoute, claudeAdvisorSetting } from "./advisor";

const claude = (model: string) => ({ provider: "claude" as const, model });
const luna = { provider: "codex" as const, model: "gpt-6-luna" };

it("lets Claude ask a Claude advisor itself only when it can guide Claude's model", () => {
  expect(advisorRoute("claude", "sonnet", claude("opus"))).toEqual({
    via: "claude",
    model: "opus",
  });
  // Default runs whatever Claude Code's settings say: Claude Code decides.
  expect(advisorRoute("claude", "", claude("opus"))?.via).toBe("claude");
  // The API refuses a weaker advisor, so Relay asks it instead.
  expect(advisorRoute("claude", "claude-fable-5-2", claude("opus"))).toEqual({
    via: "relay",
    advisor: claude("opus"),
  });
});

it("asks every other pair through Relay", () => {
  expect(advisorRoute("claude", "sonnet", luna)?.via).toBe("relay");
  expect(advisorRoute("codex", "gpt-6-luna", claude("fable"))?.via).toBe(
    "relay",
  );
  expect(advisorRoute("codex", "gpt-6-luna", "off")).toBeUndefined();
  expect(advisorRoute("codex", "gpt-6-luna", undefined)).toBeUndefined();
});

it("switches Claude's own advisor off while Relay asks another", () => {
  expect(claudeAdvisorSetting(claude("opus"), "sonnet")).toBe("opus");
  expect(claudeAdvisorSetting(luna, "sonnet")).toBe("");
  expect(claudeAdvisorSetting("off", "sonnet")).toBe("");
  // Not picked here: Claude Code's own setting stands.
  expect(claudeAdvisorSetting(undefined, "sonnet")).toBeUndefined();
});
