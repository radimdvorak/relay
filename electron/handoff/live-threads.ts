// On the computer that took threads over: the calls the computers they came
// from make to follow them live and drive them from there.
import { z } from "zod";
import {
  handoffFolderMethods,
  handoffMethods,
  type HandoffRemoteStatus,
} from "../../shared/handoff";
import { knownMessagesSchema } from "../../shared/projects";
import { idSchema } from "../../shared/validation";
import { workspaceId } from "../../shared/workspaces";
import type { ComputerMethod } from "../../shared/remote";
import type { LiveFeed } from "./live-feed";
import type { ReceiverHost } from "./receiver";

const folderMethods = new Set<string>(handoffFolderMethods);

/** The calls a computer makes to follow and drive threads here. */
const liveThreadMethods = [
  "handoffChat",
  "handoffCall",
  "handoffStatus",
] as const satisfies readonly ComputerMethod[];

type LiveThreadMethod = (typeof liveThreadMethods)[number];

export const isLiveThreadMethod = (
  method: ComputerMethod,
): method is LiveThreadMethod =>
  (liveThreadMethods as readonly string[]).includes(method);

export class LiveThreads {
  constructor(
    private host: ReceiverHost,
    private feed: LiveFeed,
    /** The arrival under way for a handoff, if one is. */
    private arriving: (id: string) => Promise<unknown> | undefined,
  ) {}

  handle(method: LiveThreadMethod, args: unknown[], deviceId: string) {
    switch (method) {
      case "handoffChat":
        return this.chat(args, deviceId);
      case "handoffCall":
        return this.call(args, deviceId);
      case "handoffStatus":
        return this.statuses(args);
    }
  }

  /** The thread a handoff from the device made here; no other device's. */
  fromDevice(id: string, deviceId: string) {
    const chat = this.host.chats.handoffs.handedOver(id);
    if (!chat || chat.cameFrom?.deviceId !== deviceId)
      throw new Error("This computer has no such thread from you.");
    return chat;
  }

  /** The thread as written here; the device follows it live from now on (`handoffChat`). */
  private chat(args: unknown[], deviceId: string) {
    const chat = this.fromDevice(idSchema.parse(args[0]), deviceId);
    const known =
      args[1] == null ? undefined : knownMessagesSchema.parse(args[1]);
    return this.feed.follow(chat, known);
  }

  /** One of `handoffMethods` on the thread, as its own window would call it (`handoffCall`). */
  private async call(args: unknown[], deviceId: string) {
    const chat = this.fromDevice(idSchema.parse(args[0]), deviceId);
    const method = z.enum(handoffMethods).parse(args[1]);
    const rest = z.array(z.unknown()).max(9).parse(args[2]).slice(1);
    if (!this.host.dispatch) throw new Error("This Relay can't do that.");
    // A folder call names the thread's worktree as this computer has it.
    const first = folderMethods.has(method)
      ? workspaceId(chat.projectId, chat.id)
      : chat.id;
    return this.host.dispatch(method, [
      first,
      ...(await this.host.chats.liveHandoff.inbound(chat.id, rest)),
    ]);
  }

  /** How each thread stands here, by handoff id; null for one that isn't here (`handoffStatus`). */
  private async statuses(args: unknown[]) {
    const ids = z.array(idSchema).max(200).parse(args[0]);
    const result: Record<string, HandoffRemoteStatus | null> = {};
    for (const id of ids) {
      // Asked mid-arrival, "no such thread" would be a lie a moment later.
      await this.arriving(id)?.catch(() => undefined);
      const found = this.host.chats.handoffs.handedOver(id);
      const live =
        found &&
        this.host.chats.list(found.projectId).find((c) => c.id === found.id);
      if (!found || !live) {
        result[id] = null;
        continue;
      }
      result[id] = {
        title: live.title,
        running: !!live.running,
        waiting: !!live.waiting,
        ...(live.asking ? { asking: true as const } : {}),
        ...(live.blocked ? { blocked: true as const } : {}),
        settled: !!live.settledAt,
        updated: live.updated,
        ...(await this.host.chats.handoffs.latestTurn(found.id)),
        returned: !!live.cameFrom?.returnedAt,
      };
    }
    return result;
  }
}
