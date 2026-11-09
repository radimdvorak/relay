import type { BrowserWindow } from "electron";

/** Windows 11 22H2, the first Windows that blurs behind a window on request. */
const firstAcrylicBuild = 22621;

/**
 * Whether this computer can show the desktop blurred behind a clear window:
 * macOS's vibrancy, or acrylic from Windows 11 22H2 on. `systemVersion` is
 * `process.getSystemVersion()`, "10.0.26200" on Windows.
 */
export function frostAvailable(
  platform: NodeJS.Platform,
  systemVersion: string,
) {
  if (platform === "darwin") return true;
  if (platform !== "win32") return false;
  const build = Number(systemVersion.split(".")[2]);
  return build >= firstAcrylicBuild;
}

export const frostHere = () =>
  frostAvailable(process.platform, process.getSystemVersion());

/**
 * Clears `win` so the frost shows through its page, or fills it with
 * `background`. macOS keeps its vibrancy on under the fill; Windows switches
 * acrylic off with it.
 */
export function setFrost(
  win: BrowserWindow,
  frosted: boolean,
  background: string,
) {
  win.setBackgroundColor(frosted ? "#00000000" : background);
  if (process.platform === "win32" && frostHere())
    win.setBackgroundMaterial(frosted ? "acrylic" : "none");
}
