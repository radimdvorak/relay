import { ContextMenu } from "@base-ui/react/context-menu";
import { Copy, Globe, Mail } from "lucide-react";
import type { ReactNode } from "react";
import { api } from "../lib/api";
import { ContextMenuItem } from "./ContextMenuItem";

/**
 * A web or mail link in rendered text. The system's own browser or mail app
 * opens it, on a click or from its right-click menu.
 */
export function WebLink({
  href,
  children,
}: {
  href: string;
  children: ReactNode;
}) {
  const mail = /^mailto:/i.test(href);
  const open = () => void api.openExternal(href).catch(() => {});
  return (
    <ContextMenu.Root>
      <ContextMenu.Trigger
        render={
          <a
            href={href}
            onClick={(e) => {
              e.preventDefault();
              open();
            }}
          />
        }
      >
        {children}
      </ContextMenu.Trigger>
      <ContextMenu.Portal>
        <ContextMenu.Positioner className="sb-menu-positioner">
          <ContextMenu.Popup className="sb-menu">
            <ContextMenuItem
              icon={mail ? <Mail size={13} /> : <Globe size={13} />}
              onClick={open}
            >
              {mail ? "Write an email" : "Open in browser"}
            </ContextMenuItem>
            <ContextMenuItem
              icon={<Copy size={13} />}
              onClick={() =>
                void api.writeClipboard(mail ? href.slice(7) : href)
              }
            >
              {mail ? "Copy address" : "Copy link"}
            </ContextMenuItem>
          </ContextMenu.Popup>
        </ContextMenu.Positioner>
      </ContextMenu.Portal>
    </ContextMenu.Root>
  );
}
