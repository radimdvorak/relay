import { execFile, spawn } from "node:child_process";
import { promisify } from "node:util";

/** Opens `file` in the app at `path`. */
export async function launch({ path }: { path: string }, file: string) {
  if (process.platform === "darwin") {
    // open's own words say why, not Node's "Command failed: open -a …".
    await promisify(execFile)("open", ["-a", path, file], {
      timeout: 15_000,
    }).catch((e: Error & { stderr?: string }) => {
      throw new Error(e.stderr?.trim() || e.message);
    });
    return;
  }
  // Detached, so the app outlives Relay and its windows aren't Relay's.
  await new Promise<void>((resolve, reject) => {
    const child = spawn(path, [file], { detached: true, stdio: "ignore" });
    child.once("error", reject);
    child.once("spawn", () => {
      child.unref();
      resolve();
    });
  });
}
