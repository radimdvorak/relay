import { expect, it, vi } from "vitest";
import { pasteText } from "./terminal-clipboard";

vi.mock("../../lib/api", () => ({ api: {} }));

it("drops the line break a single copied command ends with", () => {
  expect(pasteText("ssh host 'ls'\n")).toBe("ssh host 'ls'");
  expect(pasteText("ssh host 'ls'\r\n\n")).toBe("ssh host 'ls'");
});

it("keeps several lines as they were copied", () => {
  expect(pasteText("cd /tmp\nls\n")).toBe("cd /tmp\nls\n");
});
