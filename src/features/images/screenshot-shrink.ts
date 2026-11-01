import CompressWorker from "./screenshot-compress.worker?worker";

export { LIMIT } from "./screenshot-compress";

type Compressed = { blob: Blob } | { error: string };

/**
 * Compresses off the main thread. A page's own canvas encodes JPEGs in idle
 * time, and a quiet window gets so little of it that one large screenshot
 * took up to 7s; a worker takes about 100ms.
 */
export function shrink(file: Blob): Promise<Blob> {
  const worker = new CompressWorker();
  return new Promise<Blob>((resolve, reject) => {
    worker.onmessage = ({ data }: MessageEvent<Compressed>) =>
      "blob" in data ? resolve(data.blob) : reject(new Error(data.error));
    worker.onerror = () => reject(new Error("Could not compress screenshot."));
    worker.postMessage(file);
  }).finally(() => worker.terminate());
}
