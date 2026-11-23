// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react";
import { AudioTile } from "./AudioTile";
import { isAudioFile, resolveAudioMime } from "./draft-audio";

class FakeAudio extends EventTarget {
  static instances: FakeAudio[] = [];
  src = "";
  preload = "";
  currentTime = 0;
  duration = 18;
  paused = true;
  playCalls = 0;
  pauseCalls = 0;
  failNextPlay = false;

  constructor(src?: string) {
    super();
    if (src) this.src = src;
    FakeAudio.instances.push(this);
  }

  async play() {
    this.playCalls++;
    if (this.failNextPlay) throw new Error("Decode failed");
    this.paused = false;
    this.dispatchEvent(new Event("play"));
  }

  pause() {
    this.pauseCalls++;
    if (this.paused) return;
    this.paused = true;
    this.dispatchEvent(new Event("pause"));
  }
}

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  (
    globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
  ).IS_REACT_ACT_ENVIRONMENT = true;
  FakeAudio.instances = [];
  vi.stubGlobal("Audio", FakeAudio);
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

describe("resolveAudioMime and isAudioFile", () => {
  it("accepts audio files by MIME or extension and rejects video/webm", () => {
    const wav = new File(["x"], "clip.wav", { type: "audio/x-wav" });
    const webmAudio = new File(["x"], "voice.webm", {
      type: "audio/webm;codecs=opus",
    });
    const webmVideo = new File(["x"], "screen.webm", { type: "video/webm" });
    const extOnly = new File(["x"], "demo.flac", { type: "" });
    const octet = new File(["x"], "note.m4a", {
      type: "application/octet-stream",
    });

    expect(resolveAudioMime(wav)).toBe("audio/wav");
    expect(resolveAudioMime(webmAudio)).toBe("audio/webm");
    expect(resolveAudioMime(extOnly)).toBe("audio/flac");
    expect(resolveAudioMime(octet)).toBe("audio/mp4");
    expect(isAudioFile(webmVideo)).toBe(false);
  });
});

describe("AudioTile", () => {
  it("renders filename, format badge, 24 bars, duration, and remove button", () => {
    const onRemove = vi.fn();
    act(() => {
      root.render(
        <AudioTile
          name="standup-note.wav"
          mimeType="audio/wav"
          durationSec={18}
          src="data:audio/wav;base64,UklGRg=="
          onRemove={onRemove}
        />,
      );
    });

    expect(container.querySelector(".audio-tile-name")?.textContent).toBe(
      "standup-note.wav",
    );
    expect(container.querySelector(".audio-tile-format")?.textContent).toBe(
      "WAV",
    );
    expect(container.querySelector(".audio-tile-time")?.textContent).toBe(
      "0:18",
    );
    expect(container.querySelectorAll(".audio-tile-bar")).toHaveLength(24);

    const removeBtn = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Remove standup-note.wav"]',
    )!;
    act(() => removeBtn.click());
    expect(onRemove).toHaveBeenCalledOnce();
  });

  it("loads lazily, plays/pauses, pauses other active tiles, and seeks on waveform click", async () => {
    const loadFirst = vi.fn(async () => "relay-media://first");
    const loadSecond = vi.fn(async () => "relay-media://second");

    act(() => {
      root.render(
        <div>
          <AudioTile
            name="first.wav"
            mimeType="audio/wav"
            durationSec={20}
            load={loadFirst}
          />
          <AudioTile
            name="second.mp3"
            mimeType="audio/mpeg"
            durationSec={10}
            load={loadSecond}
          />
        </div>,
      );
    });

    const playFirst = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Play first.wav"]',
    )!;
    const playSecond = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Play second.mp3"]',
    )!;

    await act(async () => {
      playFirst.click();
    });
    expect(loadFirst).toHaveBeenCalledOnce();
    expect(FakeAudio.instances[0]!.paused).toBe(false);

    // Seeking the first tile's waveform updates currentTime proportionally.
    const firstWave = container.querySelectorAll<HTMLDivElement>(
      ".audio-tile-wave",
    )[0]!;
    vi.spyOn(firstWave, "getBoundingClientRect").mockReturnValue({
      left: 0,
      width: 100,
      top: 0,
      right: 100,
      bottom: 24,
      height: 24,
      x: 0,
      y: 0,
      toJSON: () => {},
    });
    await act(async () => {
      firstWave.dispatchEvent(
        new MouseEvent("click", { bubbles: true, clientX: 50 }),
      );
    });
    expect(FakeAudio.instances[0]!.currentTime).toBe(9);

    // Starting the second tile pauses the first.
    await act(async () => {
      playSecond.click();
    });
    expect(loadSecond).toHaveBeenCalledOnce();
    expect(FakeAudio.instances[0]!.paused).toBe(true);
    expect(FakeAudio.instances[1]!.paused).toBe(false);
  });

  it("shows Error when loading fails", async () => {
    const load = vi.fn(async () => {
      throw new Error("Missing file");
    });

    act(() => {
      root.render(
        <AudioTile
          name="broken.wav"
          mimeType="audio/wav"
          durationSec={5}
          load={load}
        />,
      );
    });

    const playBtn = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Play broken.wav"]',
    )!;
    await act(async () => {
      playBtn.click();
    });

    expect(container.querySelector(".audio-tile-time")?.textContent).toBe(
      "Error",
    );
  });
});

describe("QueuedBody audio", () => {
  it("renders queued audio clips as playable AudioTile rows even without text", async () => {
    const { QueryClient, QueryClientProvider } =
      await import("@tanstack/react-query");
    const { QueuedBody } = await import("../thread/QueuedBody");
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={qc}>
          <QueuedBody
            input={{
              id: "q1",
              body: "",
              provider: "antigravity",
              choice: { model: "default", fast: false, reasoningEffort: "" },
              runtimeMode: "auto",
              interactionMode: "default",
              audio: [
                {
                  name: "voice-memo.wav",
                  mimeType: "audio/wav",
                  durationSec: 12,
                  peaks: [0.3, 0.7],
                  dataUrl: "data:audio/wav;base64,UklGRg==",
                },
              ],
            }}
            projectRoot="/tmp/relay"
            onOpenFile={() => {}}
          />
        </QueryClientProvider>,
      );
    });

    expect(container.querySelector(".audio-tile-name")?.textContent).toBe(
      "voice-memo.wav",
    );
    expect(container.querySelector(".audio-tile-time")?.textContent).toBe(
      "0:12",
    );
    const playBtn = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Play voice-memo.wav"]',
    )!;
    await act(async () => {
      playBtn.click();
    });
    expect(FakeAudio.instances[0]?.src).toBe("data:audio/wav;base64,UklGRg==");
  });
});
