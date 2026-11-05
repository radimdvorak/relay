// @vitest-environment happy-dom
import { expect, it, vi } from "vitest";

vi.mock("../../lib/api", () => ({
  api: {
    projectFile: vi.fn(async (_where: string, path: string) => ({
      path,
      branch: "main",
      head: "abc",
      version: "v1",
      contents: `${path}\n`,
      original: `${path}\n`,
    })),
    localHistoryRecord: vi.fn(async () => null),
  },
}));

const { documents } = await import("../editor/documents");
const { moveTabDocuments } = await import("./tab-documents");

it("takes the moved file's edits to a path whose deleted file's tab is still open", async () => {
  const source = { kind: "project" as const, where: "w" };
  const moving = documents.get(source, "a.txt", () => []);
  const stale = documents.get(source, "b.txt", () => []);
  await vi.waitFor(() => expect(moving.state && stale.state).toBeDefined());
  moving.edit({ changes: { from: 0, insert: "edited\n" }, userEvent: "input" });
  expect(moving.status.dirty).toBe(true);

  moveTabDocuments(
    [{ path: "a.txt" }, { path: "b.txt" }],
    { where: "w" },
    "a.txt",
    "b.txt",
    false,
  );

  expect(documents.find(source, "b.txt")).toBe(moving);
  expect(documents.find(source, "a.txt")).toBeUndefined();
  expect(documents.unsaved()).toEqual([moving]);
});
