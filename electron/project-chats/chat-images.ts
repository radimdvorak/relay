import { copyFile, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";
import { join } from "node:path";
import type {
  ChatImage,
  ChatMessage,
  ProjectChatSend,
} from "../../shared/projects";
import { imageMimeType } from "./images";

const uuidPattern =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** A thread's saved screenshots under `<dir>/images/<chatId>/`. */
export class ChatImageFiles {
  constructor(private dir: string) {}

  path(chatId: string, image: ChatImage) {
    if (!uuidPattern.test(image.id))
      throw new Error("Invalid saved screenshot identity.");
    const ext =
      image.mimeType === "image/jpeg" ? "jpg" : image.mimeType.split("/")[1];
    return join(this.dir, "images", chatId, `${image.id}.${ext}`);
  }

  /** A thread's screenshot as a data URL. */
  async read(chatId: string, image: ChatImage) {
    const bytes = await readFile(this.path(chatId, image));
    return `data:${image.mimeType};base64,${bytes.toString("base64")}`;
  }

  async save(
    chatId: string,
    images: NonNullable<ProjectChatSend["images"]>,
  ): Promise<ChatImage[]> {
    const checked = images.map(({ name, mimeType, dataUrl, browser }) => {
      const bytes = Buffer.from(
        dataUrl.slice(dataUrl.indexOf(",") + 1),
        "base64",
      );
      if (imageMimeType(bytes) !== mimeType || bytes.length > 800_000)
        throw new Error("Screenshot is invalid or exceeds the 800 KB limit.");
      return {
        meta: {
          id: randomUUID(),
          name,
          mimeType,
          sizeBytes: bytes.length,
          ...(browser ? { browser } : {}),
        } as ChatImage,
        bytes,
      };
    });
    await this.folder(chatId);
    for (const { meta, bytes } of checked)
      await writeFile(this.path(chatId, meta), bytes, {
        flag: "wx",
        mode: 0o600,
      });
    return checked.map(({ meta }) => meta);
  }

  /** A fork's copies of the screenshots its messages carry over. */
  async copy(from: string, to: string, messages: ChatMessage[]) {
    const images = messages.flatMap((m) => m.images ?? []);
    if (!images.length) return;
    await this.folder(to);
    for (const image of images)
      await copyFile(this.path(from, image), this.path(to, image));
  }

  removeAll(chatId: string) {
    return rm(join(this.dir, "images", chatId), {
      recursive: true,
      force: true,
    });
  }

  private folder(chatId: string) {
    return mkdir(join(this.dir, "images", chatId), {
      recursive: true,
      mode: 0o700,
    });
  }
}
