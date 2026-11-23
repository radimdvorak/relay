// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react";
import type { DraftAudio } from "../audio/draft-audio";
import { ComposerAttachmentStrip } from "./ComposerAttachmentStrip";

const clip: DraftAudio = {
  id: "clip-1",
  name: "voice-note.wav",
  mimeType: "audio/wav",
  sizeBytes: 128,
  durationSec: 12,
  dataUrl: "data:audio/wav;base64,UklGRg==",
};

function makeDraft(overrides: Record<string, unknown> = {}) {
  return {
    attached: [],
    audio: [clip],
    pastes: [],
    incoming: 0,
    preparing: false,
    error: undefined,
    removeImage: vi.fn(),
    removeAudio: vi.fn(),
    clearAudio: vi.fn(),
    sketch: vi.fn(),
    ...overrides,
  };
}

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  (
    globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
  ).IS_REACT_ACT_ENVIRONMENT = true;
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("ComposerAttachmentStrip audio compatibility warning", () => {
  it("shows no warning for audio-capable or registry ACP agents", () => {
    for (const to of ["antigravity", "acp:custom"] as const) {
      act(() => {
        root.render(
          <ComposerAttachmentStrip
            draft={makeDraft() as never}
            to={to}
            onSwitchAgent={vi.fn()}
            onOpenPaste={vi.fn()}
            onRemovePaste={vi.fn()}
          />,
        );
      });
      expect(container.querySelector(".composer-audio-warning")).toBeNull();
      expect(
        container
          .querySelector(".audio-tile")
          ?.getAttribute("data-unsupported"),
      ).toBeNull();
    }
  });

  it("warns and offers quick switch or removal when switched to a non-audio agent", () => {
    const draft = makeDraft();
    const onSwitchAgent = vi.fn();
    act(() => {
      root.render(
        <ComposerAttachmentStrip
          draft={draft as never}
          to="claude"
          onSwitchAgent={onSwitchAgent}
          onOpenPaste={vi.fn()}
          onRemovePaste={vi.fn()}
        />,
      );
    });

    const warning = container.querySelector(".composer-audio-warning");
    expect(warning?.textContent).toContain("Claude can't listen to audio.");
    expect(
      container
        .querySelector(".audio-tile")
        ?.getAttribute("data-unsupported"),
    ).toBe("true");

    const buttons = Array.from(warning!.querySelectorAll("button"));
    const useAgy = buttons.find((b) => b.textContent === "Use Antigravity")!;
    const removeAudio = buttons.find((b) => b.textContent === "Remove audio")!;
    act(() => useAgy.click());
    expect(onSwitchAgent).toHaveBeenCalledWith("antigravity");

    act(() => removeAudio.click());
    expect(draft.clearAudio).toHaveBeenCalledOnce();
  });

  it("explains that notes cannot include audio when switched to message", () => {
    act(() => {
      root.render(
        <ComposerAttachmentStrip
          draft={makeDraft() as never}
          to="message"
          onSwitchAgent={vi.fn()}
          onOpenPaste={vi.fn()}
          onRemovePaste={vi.fn()}
        />,
      );
    });

    expect(
      container.querySelector(".composer-audio-warning")?.textContent,
    ).toContain("Notes can't include audio.");
  });
});
