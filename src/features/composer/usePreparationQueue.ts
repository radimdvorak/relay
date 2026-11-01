import { useRef, useState } from "react";

/**
 * Attachments wait their turn so each batch counts the ones before it, and
 * Send waits until everything pasted has landed in the draft.
 */
export function usePreparationQueue(setError: (error: undefined) => void) {
  const [preparing, setPreparing] = useState(false);
  const preparation = useRef<Promise<void>>(Promise.resolve());
  const batches = useRef(0);
  return {
    /** An attachment is being prepared; the message waits for it. */
    preparing,
    /** Also catches a send in the same event turn, before React rerenders. */
    isPreparing: () => batches.current > 0,
    queuePreparation(attach: () => Promise<void>) {
      batches.current++;
      setPreparing(true);
      setError(undefined);
      preparation.current = preparation.current.then(attach).finally(() => {
        if (!--batches.current) setPreparing(false);
      });
    },
  };
}
