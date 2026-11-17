import type { Handlers } from "./context";

// Both helpers carry what they've found so far as an argument, so their
// recursion is a tail call: TypeScript follows those 1000 deep instead of 50,
// and the API has nearly 50 groups.

/** Method names more than one group handles. */
type HandledTwice<
  G extends readonly unknown[],
  Seen extends PropertyKey = never,
  Twice extends PropertyKey = never,
> = G extends readonly [infer Head, ...infer Rest]
  ? HandledTwice<Rest, Seen | keyof Head, Twice | (keyof Head & Seen)>
  : Twice;

type Merged<G extends readonly unknown[], All = unknown> = G extends readonly [
  infer Head,
  ...infer Rest,
]
  ? Merged<Rest, All & Head>
  : All;

/**
 * One table from the domain groups. A spread would let the last group that
 * names a method quietly win, so a method two groups handle doesn't compile.
 */
export function combine<const G extends readonly Handlers[]>(
  groups: G &
    ([HandledTwice<G>] extends [never]
      ? unknown
      : { handledTwice: HandledTwice<G> }),
): Merged<G> {
  return Object.assign({}, ...groups);
}
