/** The most a screenshot may weigh once stored. */
export const LIMIT = 800_000;
/** A screenshot is drawn no longer than this on its long side before compressing. */
const LONG_SIDE = 2048;

/** Tried in order until one fits: quality goes first, then size. */
const attempts = [
  { size: 1, quality: 0.88 },
  { size: 1, quality: 0.78 },
  { size: 1, quality: 0.66 },
  { size: 0.75, quality: 0.78 },
  { size: 0.55, quality: 0.78 },
];

/** Re-encodes an image too heavy to keep as a JPEG under LIMIT, shrinking it if it must. */
export async function compress(file: Blob): Promise<Blob> {
  const bitmap = await createImageBitmap(file).catch(() => {
    throw new Error("Screenshot is too large or damaged to open.");
  });
  try {
    const canvas = new OffscreenCanvas(1, 1);
    const context = canvas.getContext("2d");
    if (!context) throw new Error("Could not compress screenshot.");
    const fit = Math.min(1, LONG_SIDE / Math.max(bitmap.width, bitmap.height));
    let drawnAt: number | undefined;
    for (const { size, quality } of attempts) {
      if (size !== drawnAt) {
        // Resizing clears the canvas; JPEG has no alpha, so paint white under it.
        canvas.width = Math.max(1, Math.round(bitmap.width * fit * size));
        canvas.height = Math.max(1, Math.round(bitmap.height * fit * size));
        context.fillStyle = "#fff";
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
        drawnAt = size;
      }
      const blob = await canvas.convertToBlob({ type: "image/jpeg", quality });
      if (blob.size <= LIMIT) return blob;
    }
    throw new Error("Screenshot is too large after compression.");
  } finally {
    bitmap.close();
  }
}
