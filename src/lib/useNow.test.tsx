// @vitest-environment happy-dom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, expect, it, vi } from "vitest";
import { useNow } from "./useNow";

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

let root: Root;

afterEach(async () => {
  await act(async () => root?.unmount());
  vi.useRealTimers();
  vi.restoreAllMocks();
});

it("keeps ticking on blur and honors disabled clocks", async () => {
  vi.useFakeTimers();
  vi.setSystemTime(0);
  vi.spyOn(document, "hasFocus").mockImplementation(() => false);
  let now = 0;
  function Probe({ enabled }: { enabled: boolean }) {
    now = useNow(1000, enabled);
    return null;
  }
  root = createRoot(document.createElement("div"));
  await act(async () => root.render(<Probe enabled />));
  await act(async () => window.dispatchEvent(new Event("blur")));
  await act(async () => vi.advanceTimersByTime(3000));
  expect(now).toBe(3000);
  await act(async () => root.render(<Probe enabled={false} />));
  await act(async () => vi.advanceTimersByTime(5000));
  expect(now).toBe(3000);
  await act(async () => root.render(<Probe enabled />));
  expect(now).toBe(8000);
});
