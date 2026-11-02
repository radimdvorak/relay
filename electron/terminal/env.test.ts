import { afterEach, expect, it, vi } from "vitest";
import { inheritedEnv } from "./env";

afterEach(() => vi.unstubAllEnvs());

it("leaves out the editor and terminal of the session Relay was started from", () => {
  vi.stubEnv("EDITOR", "vi");
  vi.stubEnv("ITERM_SESSION_ID", "w0t0p0");
  vi.stubEnv("LC_TERMINAL", "iTerm2");
  vi.stubEnv("RELAY_DEV_URL", "http://127.0.0.1:5177");
  vi.stubEnv("NVM_DIR", "/Users/someone/.nvm");
  const env = inheritedEnv();
  expect(env).not.toHaveProperty("EDITOR");
  expect(env).not.toHaveProperty("ITERM_SESSION_ID");
  expect(env).not.toHaveProperty("LC_TERMINAL");
  expect(env).not.toHaveProperty("RELAY_DEV_URL");
  expect(env.NVM_DIR).toBe("/Users/someone/.nvm");
});
