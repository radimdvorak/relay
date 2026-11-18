import { ContextMenu } from "@base-ui/react/context-menu";
import { Copy, ExternalLink, FileText, FolderOpen, Globe } from "lucide-react";
import { createContext, useContext, type ReactElement } from "react";
import {
  splitOutsidePath,
  type ProjectFileLink,
} from "../../shared/project-file-links";
import { api } from "../lib/api";
import { revealLabel } from "../lib/mod-key";
import { ContextMenuItem } from "./ContextMenuItem";

/**
 * Whether the files chips name are on this computer's disk. A thread running
 * on another computer has them there, and opening the copy here would show
 * the wrong file.
 */
export const FilesOnThisComputer = createContext(true);

/** Files a browser shows as a page. */
const PAGE = /\.(html?|xhtml|svg)$/i;

/** A file chip's right-click menu: open it here, in the system's apps, or copy its path. */
export function FileLinkMenu({
  target,
  fullPath,
  onOpen,
  children,
}: {
  target: ProjectFileLink;
  /** Where the file is on disk, which the system's apps open it by. */
  fullPath: string;
  onOpen: (target: ProjectFileLink) => void;
  /** The chip, which opens the menu on a right-click. */
  children: ReactElement;
}) {
  const here = useContext(FilesOnThisComputer);
  const { folder, name } = splitOutsidePath(fullPath);
  const file = !target.directory;
  const outside = (open: Promise<void>) =>
    void open.catch((e) => console.warn(`Couldn't open ${fullPath}`, e));
  return (
    <ContextMenu.Root>
      <ContextMenu.Trigger render={children} />
      <ContextMenu.Portal>
        <ContextMenu.Positioner className="sb-menu-positioner">
          <ContextMenu.Popup className="sb-menu">
            <ContextMenuItem
              icon={<FileText size={13} />}
              onClick={() => onOpen(target)}
            >
              Open
            </ContextMenuItem>
            {here && file && PAGE.test(name) && (
              <ContextMenuItem
                icon={<Globe size={13} />}
                onClick={() =>
                  outside(api.openProjectPathInBrowser(folder, name))
                }
              >
                Open in browser
              </ContextMenuItem>
            )}
            {here && file && (
              <ContextMenuItem
                icon={<ExternalLink size={13} />}
                onClick={() => outside(api.openProjectPath(folder, name))}
              >
                Open with default app
              </ContextMenuItem>
            )}
            {here && (
              <ContextMenuItem
                icon={<FolderOpen size={13} />}
                onClick={() => outside(api.revealProjectPath(folder, name))}
              >
                {revealLabel}
              </ContextMenuItem>
            )}
            <ContextMenu.Separator className="sb-menu-separator" />
            <ContextMenuItem
              icon={<Copy size={13} />}
              onClick={() => void api.writeClipboard(fullPath)}
            >
              Copy path
            </ContextMenuItem>
          </ContextMenu.Popup>
        </ContextMenu.Positioner>
      </ContextMenu.Portal>
    </ContextMenu.Root>
  );
}
