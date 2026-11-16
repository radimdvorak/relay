import type { ReactNode } from "react";

/** The composer's errors, one line each, for the ones there are. */
export function ComposerAlerts({ errors }: { errors: ReactNode[] }) {
  return errors.filter(Boolean).map((error, i) => (
    <p key={i} role="alert" className="composer-image-error">
      {error}
    </p>
  ));
}
