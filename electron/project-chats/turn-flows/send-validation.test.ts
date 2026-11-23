import { describe, expect, it } from "vitest";
import type { ProjectChatSend } from "../../../shared/projects";
import { validateSend } from "./send-validation";

const clip: NonNullable<ProjectChatSend["audio"]>[number] = {
  name: "voice.wav",
  mimeType: "audio/wav",
  dataUrl: "data:audio/wav;base64,UklGRg==",
};

const sendInput = (
  extra: Partial<ProjectChatSend>,
): ProjectChatSend => ({
  id: "chat-1",
  body: "",
  provider: "antigravity",
  choice: { model: "", reasoningEffort: "", fast: false },
  runtimeMode: "approval-required",
  interactionMode: "default",
  ...extra,
});

describe("validateSend audio rules", () => {
  it("allows an audio-only prompt addressed to an audio-capable agent", () => {
    expect(() =>
      validateSend(
        { provider: "antigravity", question: "" },
        sendInput({ body: "@antigravity", audio: [clip] }),
        undefined,
      ),
    ).not.toThrow();
  });

  it("rejects audio attachments addressed to built-in agents without audio support", () => {
    expect(() =>
      validateSend(
        { provider: "claude", question: "listen" },
        sendInput({ body: "@claude listen", provider: "claude", audio: [clip] }),
        undefined,
      ),
    ).toThrow("Claude can't listen to audio.");

    expect(() =>
      validateSend(
        { provider: "codex", question: "listen" },
        sendInput({ body: "@codex listen", provider: "codex", audio: [clip] }),
        undefined,
      ),
    ).toThrow("Codex can't listen to audio.");
  });

  it("still rejects an empty mention when neither text, images, nor audio are attached", () => {
    expect(() =>
      validateSend(
        { provider: "antigravity", question: "" },
        sendInput({ body: "@antigravity" }),
        undefined,
      ),
    ).toThrow("Add a question after the agent mention.");
  });
});
