/**
 * A thread's messages as the desktop pushes them to its windows. A streaming
 * answer goes as a patch on the copy a window last got (shared/remote-delta,
 * as phones get it), so each update carries its new words instead of the
 * whole answer: a 50KB answer sent whole every 40ms cost a window over 1MB/s
 * to copy and read. Whatever isn't streaming goes whole, so a window never
 * ends on a patch it couldn't place.
 */
import type { ProjectChatEvent } from "./events";
import type { ChatMessage } from "./projects";
import {
  applyMessagePatch,
  diffMessage,
  type MessagePatch,
} from "./remote-delta";

/** A streaming answer's change since the copy a window last got; `base` is that copy's version. */
export interface ProjectChatPatchEvent {
  chatId: string;
  patch: MessagePatch;
  base: number;
  title?: string;
}

/** What goes to a window on "relay:project-chat". */
export type ProjectChatPush = ProjectChatEvent | ProjectChatPatchEvent;

/** A thread's own answer, still streaming: the only kind that goes as patches. */
const patchable = (event: ProjectChatEvent) =>
  event.message.status === "streaming" && !event.setup;

/**
 * The desktop's side: remembers what each window last got of every streaming
 * answer. A window that starts listening, or lost track, asks for `resync`.
 */
export class ChatPushSender<Target extends object> {
  private sent = new WeakMap<Target, Map<string, ChatMessage>>();

  constructor(
    /** A deep copy: the event may hold the live answer its recorder keeps writing into. */
    private copy: <T>(value: T) => T,
  ) {}

  /** What each of `targets` gets for `event`. */
  pushes(
    event: ProjectChatEvent,
    targets: Target[],
  ): [Target, ProjectChatPush][] {
    const { message } = event;
    if (!patchable(event)) {
      for (const target of targets) this.sent.get(target)?.delete(message.id);
      return targets.map((target) => [target, event]);
    }
    let whole: ChatMessage | undefined;
    // Windows that got the same copy last time get the same patch now.
    const patches = new Map<
      ChatMessage,
      { push: ProjectChatPatchEvent; held: ChatMessage }
    >();
    return targets.map((target) => {
      const sent = this.sentTo(target);
      const prev = sent.get(message.id);
      if (!prev) {
        sent.set(message.id, (whole ??= this.copy(message)));
        return [target, event];
      }
      let patched = patches.get(prev);
      if (!patched) {
        const patch = diffMessage(prev, message);
        const push: ProjectChatPatchEvent = {
          chatId: event.chatId,
          patch,
          base: prev.version,
          ...(event.title ? { title: event.title } : {}),
        };
        // What the window holds next is its copy with the patch on, as it
        // builds it too: a copy of what changed, not of the whole answer.
        const held = applyMessagePatch(prev, this.copy(patch));
        patches.set(prev, (patched = { push, held }));
      }
      sent.set(message.id, patched.held);
      return [target, patched.push];
    });
  }

  /** `target` gets every streaming answer whole next time. */
  resync(target: Target) {
    this.sent.delete(target);
  }

  private sentTo(target: Target) {
    let sent = this.sent.get(target);
    if (!sent) this.sent.set(target, (sent = new Map()));
    return sent;
  }
}

/**
 * A window's side: holds the copy of each streaming answer that the next
 * patch builds on. One it can't place asks for whole copies once, through
 * `resync`; the answer's next update then comes whole.
 */
export class ChatPushReceiver {
  private held = new Map<string, ChatMessage>();
  /** Answers whose patches couldn't be placed, waiting for a whole copy. */
  private missing = new Set<string>();

  constructor(private resync: () => void) {}

  /** The event `push` stands for; nothing for a patch on a copy this window doesn't hold. */
  take(push: ProjectChatPush): ProjectChatEvent | undefined {
    if ("message" in push) {
      this.hold(push.message, patchable(push));
      return push;
    }
    const { id } = push.patch;
    const prev = this.held.get(id);
    if (prev?.version !== push.base) {
      this.lost(id);
      return undefined;
    }
    const message = applyMessagePatch(prev, push.patch);
    this.hold(message, message.status === "streaming");
    return {
      chatId: push.chatId,
      message,
      ...(push.title ? { title: push.title } : {}),
    };
  }

  private hold(message: ChatMessage, streaming: boolean) {
    this.missing.delete(message.id);
    if (streaming) this.held.set(message.id, message);
    else this.held.delete(message.id);
  }

  private lost(id: string) {
    this.held.delete(id);
    if (this.missing.has(id)) return; // asked already
    this.missing.add(id);
    this.resync();
  }
}
