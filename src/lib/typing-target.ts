/**
 * Whether a key event is typing into a field. Pierre's code editor types
 * inside a shadow root, where `e.target` is only its host.
 */
export function isTypingTarget(e: Event) {
  const target = e.composedPath()[0];
  return (
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target instanceof HTMLSelectElement ||
    (target instanceof HTMLElement && target.isContentEditable)
  );
}
