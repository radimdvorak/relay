import type { Root } from "hast";
import type { PluggableList, Plugin } from "unified";

/**
 * Rendered trees by setup and text, so a thread opened again skips the
 * whole Markdown pipeline: the parse, GFM and math, and the step to HTML.
 * A tree takes about 35 bytes per character of its text.
 */
const kept = new Map<string, Root>();
const KEPT_CHARS = 1_000_000;
let keptChars = 0;

/** The tree `text` rendered to before with the same `setup`, if it's still kept. */
export function keptTree(setup: string, text: string): Root | undefined {
  const key = `${setup}\0${text}`;
  const tree = kept.get(key);
  if (!tree) return undefined;
  // Most recently used last, so it goes last.
  kept.delete(key);
  kept.set(key, tree);
  return tree;
}

/** A last rehype plugin that keeps the tree the pipeline ends with. */
export function keepTree(setup: string, text: string): Plugin<[], Root> {
  const key = `${setup}\0${text}`;
  return () => (tree) => {
    if (kept.has(key)) return;
    kept.set(key, tree);
    keptChars += key.length;
    for (const [old] of kept) {
      if (keptChars <= KEPT_CHARS) break;
      kept.delete(old);
      keptChars -= old.length;
    }
  };
}

/**
 * Plugins that hand react-markdown a kept tree instead of reading the text
 * again. react-markdown only rewrites URLs and raw HTML in the result, which
 * it has already done to a kept tree, so sharing one is safe.
 */
export function reuseTree(tree: Root): {
  remark: PluggableList;
  rehype: PluggableList;
} {
  return {
    remark: [
      function skipParse() {
        this.parser = () => ({ type: "root", children: [] });
      },
    ],
    rehype: [() => () => tree],
  };
}
