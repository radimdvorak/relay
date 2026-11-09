import { idSchema } from "../../shared/validation";
import { nudgeFor } from "../project-chats/peer-sends";
import { takes, type ApiContext } from "./context";

/** What threads' Claude sessions told other sessions on this computer. */
export function peerMessageHandlers({ projectChats, store }: ApiContext) {
  return {
    threadPeerMessages: takes(
      [idSchema],
      async (id) => (await projectChats.get(id)).peerMessages ?? [],
    ),
    nudgePeerThreads: takes([idSchema, idSchema], async (id, peerId) => {
      const [thread, peer] = await Promise.all([
        projectChats.get(id),
        projectChats.get(peerId),
      ]);
      await Promise.all([
        projectChats.send(thread.id, nudgeFor(thread, peer, store)),
        projectChats.send(peer.id, nudgeFor(peer, thread, store)),
      ]);
    }),
  };
}
