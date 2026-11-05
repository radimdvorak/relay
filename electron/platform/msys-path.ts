import { access } from "node:fs/promises";
import { tmpdir } from "node:os";
import { win32 } from "node:path";

/**
 * The Windows path Git Bash (MSYS) means by `path`, or null when it isn't
 * one of its own: `/tmp/x` is in the user's temp folder and `/c/x` on C:.
 * Agents on Windows run their commands in Git Bash and link files the way
 * it names them, which Node would read as `C:\tmp\x` instead.
 */
export function msysToWindows(path: string, temp = tmpdir()): string | null {
  const inTemp = /^\/tmp(?:\/(.*))?$/.exec(path);
  if (inTemp) return win32.join(temp, inTemp[1] ?? "");
  const onDrive = /^\/([a-z])(?:\/(.*))?$/i.exec(path);
  if (onDrive)
    return win32.join(`${onDrive[1].toUpperCase()}:\\`, onDrive[2] ?? "");
  return null;
}

const exists = (path: string) =>
  access(path).then(
    () => true,
    () => false,
  );

/**
 * Where the file an agent linked as `path` is on this computer. A real
 * `C:\tmp\x` wins over the Git Bash reading, since that's what Node opens.
 */
export async function linkedFileOnDisk(path: string): Promise<string> {
  if (process.platform !== "win32") return path;
  const translated = msysToWindows(path);
  if (!translated || (await exists(path))) return path;
  return translated;
}
