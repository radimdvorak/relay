import {
  mkdtemp,
  readdir,
  readFile,
  rm,
  stat,
  realpath,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { pathToFileURL } from "node:url";
import { antigravityHome, appDataDir } from "./account";

/**
 * Whether the session saved at `base` ran in `cwd`. The CLI records its
 * workspace inside the session's database; the older server wrote it beside
 * it, in a `.meta` file.
 */
async function sessionRanIn(base: string, cwd: string) {
  const metadata = await readFile(`${base}.meta`, "utf8")
    .then((text) => JSON.parse(text) as { cwd?: string })
    .catch(() => undefined);
  if (metadata) return metadata.cwd === cwd;
  let db: DatabaseSync | undefined;
  try {
    db = new DatabaseSync(`${base}.db`, { readOnly: true });
    const rows = db
      .prepare("SELECT data FROM trajectory_metadata_blob")
      .all() as { data: Uint8Array }[];
    const uri = Buffer.from(pathToFileURL(cwd).href);
    return rows.some((row) => {
      const data = Buffer.from(row.data);
      const at = data.indexOf(uri);
      // A folder inside cwd would match too; only cwd itself counts.
      return at >= 0 && data[at + uri.length] !== "/".charCodeAt(0);
    });
  } catch {
    return false;
  } finally {
    db?.close();
  }
}

/**
 * Helpers have no project cwd or Relay tools. Global hooks/MCP can start before
 * a permission request, so refuse configured profiles rather than running them.
 */
export async function antigravityHelper(home = antigravityHome()) {
  for (const [file, key] of [
    ["hooks.json", "hooks"],
    ["mcp_config.json", "mcpServers"],
  ] as const) {
    const info = await stat(join(home, "config", file)).catch(
      (error: NodeJS.ErrnoException) => {
        if (error.code === "ENOENT") return undefined;
        throw error;
      },
    );
    if (info && (!info.isFile() || info.size > 64_000))
      throw new Error(
        "Antigravity helper jobs need a profile without global hooks or MCP servers. Choose another helper model.",
      );
    const text = await readFile(join(home, "config", file), "utf8").catch(
      (error: NodeJS.ErrnoException) => {
        if (error.code === "ENOENT") return undefined;
        throw error;
      },
    );
    if (text === undefined) continue;
    let empty = false;
    try {
      const config = JSON.parse(text);
      const value = config[key] ?? config;
      empty =
        value &&
        typeof value === "object" &&
        !Array.isArray(value) &&
        Object.keys(value).length === 0;
    } catch {}
    if (!empty)
      throw new Error(
        "Antigravity helper jobs need a profile without global hooks or MCP servers. Choose another helper model.",
      );
  }
  const cwd = await realpath(
    await mkdtemp(join(tmpdir(), "relay-antigravity-helper-")),
  );
  return {
    cwd,
    async check() {
      if ((await readdir(cwd)).length)
        throw new Error(
          "Antigravity attempted file work during a text-only helper job.",
        );
    },
    async close(sessionId?: string) {
      if (
        sessionId &&
        /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(sessionId)
      ) {
        const native = join(home, appDataDir);
        const base = join(native, "conversations", sessionId);
        // The temporary cwd proves this is the helper's history, never a thread's.
        if (await sessionRanIn(base, cwd)) {
          try {
            for (const suffix of [
              ".db",
              ".db-wal",
              ".db-shm",
              ".db-journal",
              ".meta",
            ])
              await rm(`${base}${suffix}`, { force: true });
            await rm(join(native, "brain", sessionId), {
              recursive: true,
              force: true,
            });
          } catch {
            console.warn(
              "Could not remove temporary Antigravity helper history.",
            );
          }
        }
      }
      await rm(cwd, { recursive: true, force: true });
    },
  };
}
