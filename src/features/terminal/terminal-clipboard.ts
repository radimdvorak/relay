import type { Terminal } from "@xterm/xterm";
import { api } from "../../lib/api";

/**
 * A single line copied with its line break, as from a code block, would leave
 * the shell on an empty second line under the command instead of after it.
 */
export function pasteText(text: string) {
  const command = text.replace(/[\r\n]+$/, "");
  const severalLines = /[\r\n]/.test(command);
  return severalLines ? text : command;
}

/** Copy on select, right-click to copy or paste, and paste without a lone line's line break. */
export function wireClipboard(term: Terminal, element: HTMLElement) {
  // Once the mouse lets go rather than on every step of the drag.
  element.addEventListener("mouseup", (e) => {
    if (e.button === 0 && term.hasSelection())
      void api.writeClipboard(term.getSelection()).catch(() => {});
  });

  // Right-click copies a selection, or pastes when there's none, the way
  // Windows Terminal does. paste() keeps bracketed paste, so a clipboard of
  // several lines doesn't run in the shell line by line.
  element.addEventListener("contextmenu", (e) => {
    e.preventDefault();
    if (term.hasSelection()) {
      void api.writeClipboard(term.getSelection()).catch(() => {});
      term.clearSelection();
    } else {
      void api
        .readClipboard()
        .then((text) => text && term.paste(pasteText(text)))
        .catch(() => {});
    }
    term.focus();
  });

  // Ahead of xterm's own handler, which would send the text as it is.
  element.addEventListener(
    "paste",
    (e) => {
      const text = e.clipboardData?.getData("text/plain");
      if (!text) return;
      e.preventDefault();
      e.stopPropagation();
      term.paste(pasteText(text));
    },
    { capture: true },
  );
}
