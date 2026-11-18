import type { ReactNode } from "react";
import { api } from "../lib/api";

/** A web or mail link in rendered text, opened by the system's own browser or mail app. */
export function WebLink({
  href,
  children,
}: {
  href: string;
  children: ReactNode;
}) {
  return (
    <a
      href={href}
      onClick={(e) => {
        e.preventDefault();
        void api.openExternal(href).catch(() => {});
      }}
    >
      {children}
    </a>
  );
}
