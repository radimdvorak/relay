import { rm } from "node:fs/promises";
import type { ProjectChat } from "../../shared/projects";
import { reviewerTitle } from "../../shared/deep-review";
import type { Store } from "../app/store";

/** Ultraplan is gone; the hidden threads its thinkers worked in would only show up as strays. */
export async function dropThinkers(
  store: Store,
  forget: (id: string) => void,
  file: (id: string, live?: boolean) => string,
) {
  const isThinker = (c: object) => !!(c as { thinker?: unknown }).thinker;
  const strays = (store.get().chats ?? []).filter(isThinker);
  if (!strays.length) return;
  for (const { id } of strays) {
    forget(id);
    await Promise.all(
      [file(id), file(id, true)].map((path) =>
        rm(path, { force: true }).catch((e) =>
          console.warn("Could not remove a retired thinker thread:", e),
        ),
      ),
    );
  }
  await store.update((s) => {
    s.chats = (s.chats ?? []).filter((c) => !isThinker(c));
  });
}

/**
 * Deep reviewers once worked in hidden threads; now they're listed under
 * their review, as threads its agent started are. Older ones join it the
 * same way, named for what they ran on and settled with it if it is, so
 * an old review doesn't come back to Activity with them. One whose review
 * is gone stays unlisted.
 */
export async function adoptReviewers(
  store: Store,
  peek: (id: string) => Promise<ProjectChat>,
  save: (chat: ProjectChat) => Promise<void>,
) {
  const listed = store.get().chats ?? [];
  const byId = new Map(listed.map((c) => [c.id, c]));
  for (const stray of listed) {
    const review = stray.reviewer && byId.get(stray.reviewer.parent);
    if (!review || stray.startedBy) continue;
    try {
      const [parent, chat] = await Promise.all([
        peek(review.id),
        peek(stray.id),
      ]);
      // Reviewers from before rounds all belong to the first one.
      const state = parent.reviews?.[0];
      const slot = chat.reviewer?.slot ?? -1;
      const reviewer = state?.reviewers[slot];
      chat.startedBy = {
        chatId: parent.id,
        agent: state?.lead.provider ?? review.provider ?? "claude",
      };
      if (reviewer?.chatId === chat.id)
        chat.title = reviewerTitle(slot, reviewer);
      if (review.settledAt && !chat.settledAt)
        chat.settledAt = review.settledAt;
      await save(chat);
    } catch (e) {
      console.warn("Could not list an older reviewer under its review:", e);
    }
  }
}
