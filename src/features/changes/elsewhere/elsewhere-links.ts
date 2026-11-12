import type { ElsewhereGroup } from "../../../../shared/elsewhere";
import {
  matchLink,
  type ProjectFileLink,
} from "../../../../shared/project-file-links";
import type { ElsewherePick } from "./useElsewhere";

const folderName = (root: string) => root.split(/[/\\]/).at(-1) ?? root;

const slashed = (path: string) => path.replaceAll("\\", "/");

/**
 * The one file the thread changed in another repository that a chat link
 * names. Agents name such a file by its bare name, by its path in that
 * repository, by the repository's folder and that path, or by its absolute
 * path. None when the link names no such file, or several.
 */
export function elsewhereFileOf(
  link: ProjectFileLink,
  groups: readonly ElsewhereGroup[],
): ElsewherePick | undefined {
  if (link.directory) return;
  const named = link.outside ? { ...link, path: slashed(link.path) } : link;
  const picks: ElsewherePick[] = [];
  for (const group of groups)
    for (const file of group.files) {
      const ways = link.outside
        ? [`${slashed(group.root)}/${file.path}`]
        : [file.path, `${folderName(group.root)}/${file.path}`];
      if (matchLink(named, ways).length)
        picks.push({ root: group.root, path: file.path });
    }
  return picks.length === 1 ? picks[0] : undefined;
}

/**
 * The other repository the thread changed files in that a chat link names
 * as a folder, by its folder's name or the name Relay knows it by.
 */
export function elsewhereFolderOf(
  link: ProjectFileLink,
  groups: readonly ElsewhereGroup[],
): string | undefined {
  if (!link.directory || link.outside) return;
  const named = groups.filter(
    (group) => link.path === folderName(group.root) || link.path === group.name,
  );
  return named.length === 1 ? named[0].root : undefined;
}
