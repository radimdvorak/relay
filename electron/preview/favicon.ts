import type { Session } from "electron";

const ICON_LIMIT = 1024 * 1024;

/** The first of a page's icons that loads, as a data URL; undefined when none does. */
export async function faviconData(session: Session, urls: string[]) {
  for (const url of urls) {
    try {
      const response = await session.fetch(url);
      if (!response.ok) continue;
      const mime = response.headers.get("content-type")?.split(";")[0];
      if (!mime?.startsWith("image/")) continue;
      const bytes = await bodyUpTo(response, ICON_LIMIT);
      if (!bytes?.length) continue;
      return `data:${mime};base64,${bytes.toString("base64")}`;
    } catch {
      // Missing icons should never stop a page from loading.
    }
  }
  return undefined;
}

/** The response's body, or undefined as soon as it runs past `limit` bytes. */
async function bodyUpTo(response: Response, limit: number) {
  if (!response.body) return Buffer.alloc(0);
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let length = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) return Buffer.concat(chunks);
    length += value.length;
    if (length > limit) {
      await reader.cancel();
      return undefined;
    }
    chunks.push(value);
  }
}
