import { TURN_FOLDS } from "../../lib/thread-storage";
import { useTurnFlag } from "../../lib/useTurnFlag";

/** Whether a turn's changed files are folded away, kept per thread on this device. */
export function useFoldedTurn(chatId: string | undefined, messageId: string) {
  return useTurnFlag(TURN_FOLDS, chatId, messageId);
}
