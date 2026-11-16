// The secrets users gave agents through request_secret: one file each in
// Relay's own data folder, outside every project and worktree, readable only
// by this user and gone after a day.
import { randomBytes } from "node:crypto";
import { mkdir, readdir, rm, stat, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { SECRET_FILE_LIFE_HOURS } from "../../shared/secret-request";

const SECRET_FILE_LIFE_MS = SECRET_FILE_LIFE_HOURS * 60 * 60 * 1000;
const SWEEP_EVERY_MS = 60 * 60 * 1000;
const FILE_NAME = /^[0-9a-f]{32}$/;

export class SecretFiles {
  constructor(
    private dir: string,
    private now: () => number = Date.now,
  ) {}

  /** Writes the value to a new file and returns its path. */
  async save(value: string) {
    await mkdir(this.dir, { recursive: true, mode: 0o700 });
    await this.removeExpired();
    const file = join(this.dir, randomBytes(16).toString("hex"));
    await writeFile(file, value, { flag: "wx", mode: 0o600 });
    return file;
  }

  remove(file: string) {
    return rm(file, { force: true });
  }

  /** Removes the files older than a day, now and every hour while Relay runs. */
  keepSwept() {
    const sweep = () =>
      void this.removeExpired().catch((e) =>
        console.warn("Could not remove expired secret files:", e),
      );
    sweep();
    setInterval(sweep, SWEEP_EVERY_MS).unref();
  }

  async removeExpired() {
    const names = await readdir(this.dir).catch(() => []);
    for (const name of names.filter((n) => FILE_NAME.test(n))) {
      const file = join(this.dir, name);
      const written = await stat(file).then(
        (s) => s.mtimeMs,
        () => undefined,
      );
      const expired =
        written !== undefined && this.now() - written > SECRET_FILE_LIFE_MS;
      if (expired) await this.remove(file);
    }
  }
}

const byDir = new Map<string, SecretFiles>();

/** The one set of secret files under Relay's data folder `dataDir`, beside state.json. */
export function secretFiles(dataDir: string) {
  const dir = join(dataDir, "secrets");
  const existing = byDir.get(dir);
  if (existing) return existing;
  const made = new SecretFiles(dir);
  made.keepSwept();
  byDir.set(dir, made);
  return made;
}
