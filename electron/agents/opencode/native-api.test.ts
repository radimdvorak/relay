import { expect, it } from "vitest";
import { localServiceUrl, staleService } from "./native-api";

it("tells a service an older OpenCode left running from the installed one", () => {
  const info = (version: string) => JSON.stringify({ version, pid: 1 });
  expect(staleService("2.1.4", info("2.0.32"))).toBe(true);
  expect(staleService("2.1.4", info("2.1.4"))).toBe(false);
  // Nothing to go by: leave the service alone.
  expect(staleService("", info("2.0.32"))).toBe(false);
  expect(staleService("2.1.4", "not json")).toBe(false);
  expect(staleService("2.1.4", "{}")).toBe(false);
});

it("holds the address OpenCode reports to a local HTTP service", () => {
  expect(localServiceUrl("http://127.0.0.1:4096").href).toBe(
    "http://127.0.0.1:4096/",
  );
  // A service listening on every interface is reached by loopback.
  expect(localServiceUrl("http://0.0.0.0:4096").hostname).toBe("127.0.0.1");
  expect(localServiceUrl("http://[::]:4096").hostname).toBe("[::1]");
  for (const address of [
    "https://127.0.0.1:4096",
    "http://example.com:4096",
    "http://user:secret@127.0.0.1:4096",
  ]) {
    expect(() => localServiceUrl(address)).toThrow("local HTTP service");
  }
  expect(() => localServiceUrl("not a url")).toThrow("service address");
});
