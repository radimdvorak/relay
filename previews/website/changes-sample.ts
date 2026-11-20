// The demo turn's working tree: each file as the last commit had it and as
// the agent left it, so the editor's change bars come from a real diff.

export interface SampleChange {
  path: string;
  /** Not in the last commit. */
  added: boolean;
  committed: string;
  now: string;
}

const shortcutsBefore = `export const shortcutCommands = {
  terminal: {
    title: "Show or hide the terminal",
    group: "General",
    defaults: (mac) => [combo(mac, "meta+KeyJ")],
  },
  settle: {
    title: "Settle thread",
    group: "Threads",
    defaults: one("mod+KeyE"),
  },
};
`;

const shortcutsAfter = `export const shortcutCommands = {
  terminal: {
    title: "Show or hide the terminal",
    group: "General",
    defaults: (mac) => [combo(mac, "meta+KeyJ")],
  },
  "jump-thread": {
    title: "Open one of the first nine activity threads",
    group: "Threads",
    digits: true,
    defaults: one("mod+Digit1"),
  },
  settle: {
    title: "Settle the open thread",
    group: "Threads",
    defaults: one("mod+KeyE"),
  },
};
`;

const activityKeys = `/** ⌘1–9 open the Nth Activity card; holding ⌘ shows the numbers. */
export function useActivityKeys(cards: ChatSummary[], open: Open) {
  const [held, setHeld] = useState(false);
  useEffect(() => {
    const down = (event: KeyboardEvent) => {
      const slot = digitOf(event);
      if (slot && matches(event, "jump-thread")) open(cards[slot - 1]);
    };
    window.addEventListener("keydown", down);
    return () => window.removeEventListener("keydown", down);
  }, [cards, open]);
  return held;
}
`;

const shortcutTests = `import { describe, expect, it } from "vitest";
import { slotFor } from "./shortcuts";

describe("jump-thread", () => {
  it("opens the Nth Activity card with ⌘N", () => {
    expect(slotFor("3", threads)).toBe(threads[2]);
  });
  it("skips settled threads", () => {
    expect(slotFor("2", withSettled)).toBe(open[1]);
  });
  it("ignores an empty slot", () => {
    expect(slotFor("9", threads)).toBeUndefined();
  });
});
`;

export const sampleChanges: SampleChange[] = [
  {
    path: "shared/shortcuts.ts",
    added: false,
    committed: shortcutsBefore,
    now: shortcutsAfter,
  },
  {
    path: "src/features/sidebar/useActivityKeys.ts",
    added: true,
    committed: "",
    now: activityKeys,
  },
  {
    path: "src/lib/shortcuts.test.ts",
    added: true,
    committed: "",
    now: shortcutTests,
  },
];
