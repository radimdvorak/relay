import { describe, expect, it } from "vitest";
import { frostAvailable } from "./frost";

describe("frostAvailable", () => {
  it("is there on macOS and from Windows 11 22H2 on", () => {
    expect(frostAvailable("darwin", "15.1.0")).toBe(true);
    expect(frostAvailable("win32", "10.0.22621")).toBe(true);
    expect(frostAvailable("win32", "10.0.26200")).toBe(true);
  });

  it("isn't on Windows 10, the first Windows 11 or Linux", () => {
    expect(frostAvailable("win32", "10.0.19045")).toBe(false);
    expect(frostAvailable("win32", "10.0.22000")).toBe(false);
    expect(frostAvailable("linux", "6.8.0")).toBe(false);
  });
});
