import { mkdtemp, mkdir, writeFile, rm, access } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { pathToFileURL } from "node:url";
import { afterEach, expect, it } from "vitest";
import { antigravityHelper } from "./helper";

const roots: string[] = [];

async function home() {
  const root = await mkdtemp(join(tmpdir(), "relay-google-helper-test-"));
  roots.push(root);
  await mkdir(join(root, "config"));
  return root;
}

afterEach(async () => {
  await Promise.all(
    roots.splice(0).map((root) => rm(root, { recursive: true, force: true })),
  );
});

it.each([
  ["hooks.json", { hooks: { PreToolUse: [{ command: "echo hook" }] } }],
  ["mcp_config.json", { mcpServers: { custom: { command: "tool" } } }],
  ["hooks.json", "malformed"],
])("refuses %s before a helper process can launch it", async (file, config) => {
  const root = await home();
  await writeFile(
    join(root, file),
    typeof config === "string" ? config : JSON.stringify(config),
  );
  const helper = await antigravityHelper(root); // unrelated root files are not global config
  await helper.close();
  await writeFile(
    join(root, "config", file),
    typeof config === "string" ? config : JSON.stringify(config),
  );
  await expect(antigravityHelper(root)).rejects.toThrow("global hooks or MCP");
});

it("detects unexpected file work and removes only its temporary workspace", async () => {
  const root = await home();
  const helper = await antigravityHelper(root);
  await helper.check();
  await writeFile(join(helper.cwd, "unexpected"), "changed");
  await expect(helper.check()).rejects.toThrow("file work");
  await helper.close();
  await expect(access(helper.cwd)).rejects.toThrow();
  await expect(access(root)).resolves.toBeUndefined();
});

/** A session database the way the CLI saves one: its workspace inside a protobuf blob. */
async function savedSession(root: string, id: string, workspace: string) {
  const folder = join(root, "antigravity-acp", "conversations");
  await mkdir(folder, { recursive: true });
  const db = new DatabaseSync(join(folder, `${id}.db`));
  db.exec("CREATE TABLE trajectory_metadata_blob (id text, data blob)");
  const uri = Buffer.from(pathToFileURL(workspace).href);
  const blob = Buffer.concat([
    Buffer.from([0x0a, uri.length]),
    uri,
    Buffer.from([0x1a]),
  ]);
  db.prepare("INSERT INTO trajectory_metadata_blob VALUES ('main', ?)").run(
    blob,
  );
  db.close();
  return join(folder, `${id}.db`);
}

it("removes the history of the helper's own session, never one that ran elsewhere", async () => {
  const root = await home();
  const own = "11111111-1111-4111-8111-111111111111";
  const other = "22222222-2222-4222-8222-222222222222";
  const helper = await antigravityHelper(root);
  const ownDb = await savedSession(root, own, helper.cwd);
  await helper.close(own);
  await expect(access(ownDb)).rejects.toThrow();

  const second = await antigravityHelper(root);
  const otherDb = await savedSession(root, other, join(second.cwd, "project"));
  await second.close(other);
  await expect(access(otherDb)).resolves.toBeUndefined();
});
