import { useRef, useState } from "react";

/**
 * Attachments wait their turn so each batch counts the ones before it, and
 * Send waits until everything pasted has landed in the draft.
 */
export function usePreparationQueue(setError: (error: undefined) => void) {
  const [preparing, setPreparing] = useState(false);
  const [incoming, setIncoming] = useState(0);
  const preparation = useRef<Promise<void>>(Promise.resolve());
  const batches = useRef(0);
  return {
    /** An attachment is being prepared; the message waits for it. */
    preparing,
    /** How many screenshots are queued or being prepared, each shown as a placeholder. */
    incoming,
    /** Also catches a send in the same event turn, before React rerenders. */
    isPreparing: () => batches.current > 0,
    queuePreparation(attach: () => Promise<void>, screenshots = 0) {
      batches.current++;
      setPreparing(true);
      setIncoming((n) => n + screenshots);
      setError(undefined);
      preparation.current = preparation.current.then(attach).finally(() => {
        setIncoming((n) => n - screenshots);
        if (!--batches.current) setPreparing(false);
      });
    },
  };
}
