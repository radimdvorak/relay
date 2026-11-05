/** A way out of an error, offered as a button where the error shows. */
export interface ErrorAction {
  label: string;
  run: () => void;
}

/** An error the box showing it offers `action` on. */
export class ErrorWithAction extends Error {
  constructor(
    message: string,
    readonly action: ErrorAction,
  ) {
    super(message);
  }
}

export const actionOf = (error: unknown) =>
  error instanceof ErrorWithAction ? error.action : undefined;
