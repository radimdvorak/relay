import { randomUUID } from "node:crypto";
import { constants } from "node:fs";
import { chmod, copyFile, mkdir, stat } from "node:fs/promises";
import { basename, isAbsolute, join, relative } from "node:path";
import { maxUploadBytes } from "../../shared/remote";
import { pastesRoot } from "./pastes";
import { uploadName } from "./uploads";

type Kept = { copy: string; size: number; mtimeMs: number };

/** Copies already made, by the file they copy, so a file sent twice unchanged is copied once. */
const kept = new Map<string, Kept>();

const inside = (folder: string, path: string) => {
  const rel = relative(folder, path);
  return !!rel && !rel.startsWith("..") && !isAbsolute(rel);
};

const megabytes = (bytes: number) => `${Math.ceil(bytes / 1024 / 1024)}MB`;

/**
 * A file on this computer that the user's message names, as a copy among the
 * pastes: only those may go to a paired computer, so a dropped file reaches
 * a thread that runs on one. Null for what isn't such a file: a relative
 * path, a folder, a path this computer doesn't have, or a paste already.
 */
export async function keptToSend(path: string, at = pastesRoot()) {
  if (!at) throw new Error("Relay is still starting.");
  if (!isAbsolute(path) || inside(at, path)) return null;
  const file = await stat(path).catch(() => null);
  if (!file?.isFile()) return null;
  if (file.size > maxUploadBytes)
    throw new Error(
      `${basename(path)} is ${megabytes(file.size)}; another computer takes files up to ${megabytes(maxUploadBytes)}.`,
    );
  const key = `${at}\0${path}`;
  const known = kept.get(key);
  if (await stillTheCopyOf(known, file)) return known!.copy;
  const dir = join(at, randomUUID());
  await mkdir(dir, { recursive: true, mode: 0o700 });
  const copy = join(dir, uploadName(basename(path)));
  // A clone where the disk can make one, so even a big file copies at once.
  await copyFile(path, copy, constants.COPYFILE_FICLONE);
  await chmod(copy, 0o600);
  kept.set(key, { copy, size: file.size, mtimeMs: file.mtimeMs });
  return copy;
}

/** The copy is still there, and the file hasn't changed since it was made. */
async function stillTheCopyOf(
  known: Kept | undefined,
  file: { size: number; mtimeMs: number },
) {
  if (!known) return false;
  const unchanged = known.size === file.size && known.mtimeMs === file.mtimeMs;
  const there = await stat(known.copy).catch(() => null);
  return unchanged && there?.size === known.size;
}
