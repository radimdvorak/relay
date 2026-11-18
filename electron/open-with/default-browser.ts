import { app, shell } from "electron";
import { execFile } from "node:child_process";
import { pathToFileURL } from "node:url";
import { promisify } from "node:util";
import { errorMessage } from "../../shared/error-message";
import { launch } from "./launch";

const run = promisify(execFile);

/**
 * Opens a page on disk in the browser web links open in. Not the file's own
 * default app: a developer's .html often opens in their editor.
 */
export async function openInBrowser(file: string) {
  try {
    if (process.platform === "linux") return await launchLinuxBrowser(file);
    const browser = await app.getApplicationInfoForProtocol("https://");
    return await launch(browser, file);
  } catch (e) {
    console.warn(`[open-with] no default browser: ${errorMessage(e)}`);
  }
  // The system then picks by the file's type, which is often a browser too.
  await shell.openExternal(pathToFileURL(file).href);
}

/** Linux names its default browser by a desktop entry, which gtk-launch starts. */
async function launchLinuxBrowser(file: string) {
  const { stdout } = await run("xdg-settings", ["get", "default-web-browser"], {
    timeout: 5_000,
  });
  const entry = stdout.trim();
  if (!entry) throw new Error("xdg-settings named no browser");
  await run("gtk-launch", [entry, pathToFileURL(file).href], {
    timeout: 15_000,
  });
}
