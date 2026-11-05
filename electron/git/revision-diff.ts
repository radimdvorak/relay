import { NotText, decodeText, textLimit } from "./working-files";
import { run } from "./turn-snapshots";
import { digest } from "../util/hash";
import { imageSides, type FileSource } from "./image-pair";
import { videoSides } from "./video-pair";
import { gitBytes } from "./git";
import type { FilePair } from "../../shared/types";

/** One file between two commits or trees, as text unless it's binary or too big. */
export async function revisionDiff(
  root: string,
  from: string,
  to: string,
  path: string,
): Promise<FilePair> {
  let binary = false;
  const read = async (rev: string) => {
    const spec = `${rev}:${path}`;
    const size = await run(root, ["cat-file", "-s", spec]).then(
      (s) => Number(s.trim()),
      () => null,
    );
    if (size === null) return null;
    if (size > textLimit) {
      binary = true;
      return null;
    }
    let contents: string;
    try {
      contents = decodeText(await gitBytes(root, ["cat-file", "blob", spec]));
    } catch (e) {
      if (!(e instanceof NotText)) throw e;
      binary = true;
      return null;
    }
    return { name: path, contents, cacheKey: digest(contents) };
  };
  const [old, next] = await Promise.all([read(from), read(to)]);
  if (!binary) return { old, next, binary: false };
  const sides: [FileSource, FileSource] = [
    { name: path, spec: `${from}:${path}` },
    { name: path, spec: `${to}:${path}` },
  ];
  const [images, videos] = await Promise.all([
    imageSides(root, ...sides),
    videoSides(root, ...sides),
  ]);
  return { old: null, next: null, binary: true, images, videos };
}
