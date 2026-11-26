import { z } from "zod";
import type { AgentPlan, Fetch } from "../acp/registry/catalog";
import {
  installInto,
  installedIn,
  type InstallIo,
} from "../acp/registry/install";

/**
 * Relay runs Google's Antigravity CLI (`agy acp`), downloaded from where the
 * CLI's own updater looks. Google's older ACP server bundle wrote a skill into
 * every project it ran in; the CLI keeps to its own folders.
 */
export type { Fetch } from "../acp/registry/catalog";

/** Where the CLI's own updater asks for the newest release; the URL is in Google's binary. */
const manifestBase =
  "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests";
/** Relay runs only what comes from Google's bucket, whatever the manifest says. */
const downloadHost =
  "https://storage.googleapis.com/antigravity-public/antigravity-cli/";

export interface InstalledAntigravity {
  version: string;
  command: string;
  /** What goes before the CLI's own arguments; none for Google's binary. */
  args: string[];
}

/** Google's older ACP server, which Relay used to download into the same folder. */
const olderServer = /agy_acp_server\.(par|exe)$/;

type Release = Extract<AgentPlan, { kind: "binary" }>;

const manifestSchema = z.object({
  version: z.string().regex(/^\d+\.\d+\.\d+$/),
  url: z.url(),
  sha512: z.string().regex(/^[a-f0-9]{128}$/i),
});

/** The CLI's program in its download: a tarball's `antigravity`, or Windows' bare `.exe`. */
const programName = (platform: NodeJS.Platform) =>
  platform === "win32" ? "agy.exe" : "antigravity";

/** Names this machine the way the CLI's manifests do, if Google builds for it. */
export function cliTarget(
  platform: NodeJS.Platform,
  arch: string,
  musl = false,
) {
  if (!["darwin", "linux", "win32"].includes(platform)) return undefined;
  if (arch !== "x64" && arch !== "arm64") return undefined;
  const os = platform === "win32" ? "windows" : platform;
  const cpu = arch === "x64" ? "amd64" : "arm64";
  return `${os}_${cpu}${platform === "linux" && musl ? "_musl" : ""}`;
}

/** Linux without glibc (Alpine) gets Google's musl build. */
const usesMusl = () =>
  process.platform === "linux" &&
  !(
    process.report?.getReport() as { header?: { glibcVersionRuntime?: string } }
  )?.header?.glibcVersionRuntime;

/**
 * The URL fetch would really ask for, if it's a CLI download in Google's
 * bucket. Read as fetch reads it: `..` and `%2e%2e` would otherwise climb
 * out into any other bucket on the same host.
 */
function googlesDownload(url: string, platform: NodeJS.Platform) {
  const parsed = URL.parse(url);
  if (!parsed || parsed.search || parsed.hash) return undefined;
  const download = platform === "win32" ? ".exe" : ".tar.gz";
  const inBucket = parsed.href.startsWith(downloadHost);
  return inBucket && parsed.pathname.endsWith(download)
    ? parsed.href
    : undefined;
}

/** The release a manifest names, refused unless it's Google's own download. */
export function releaseFrom(
  manifest: unknown,
  platform: NodeJS.Platform = process.platform,
): Release {
  const found = manifestSchema.safeParse(manifest);
  if (!found.success)
    throw new Error("Google's Antigravity manifest isn't readable.");
  const { version, url, sha512 } = found.data;
  const archive = googlesDownload(url, platform);
  if (!archive)
    throw new Error(`Refusing to download Antigravity from ${url}.`);
  return {
    kind: "binary",
    id: "Antigravity",
    version,
    archive,
    cmd: programName(platform),
    sha512: sha512.toLowerCase(),
    args: [],
    env: {},
  };
}

export async function latestAntigravity(
  fetch: Fetch,
  platform: NodeJS.Platform = process.platform,
  arch: string = process.arch,
) {
  const target = cliTarget(platform, arch, usesMusl());
  if (!target)
    throw new Error(`Antigravity has no build for ${platform} ${arch}.`);
  const response = await fetch(`${manifestBase}/${target}.json`);
  if (!response.ok)
    throw new Error(
      `Couldn't ask Google for Antigravity (${response.status}).`,
    );
  return releaseFrom(await response.json(), platform);
}

/** The CLI Relay downloaded, if it's still all there; the older server doesn't count. */
export async function installedAntigravity(
  root: string,
): Promise<InstalledAntigravity | undefined> {
  const found = await installedIn(root);
  if (!found || olderServer.test(found.command)) return undefined;
  return { version: found.version, command: found.command, args: found.args };
}

interface InstallOptions {
  /** The version wanted; Google only offers its newest, so anything else fails. */
  version?: string;
  platform?: NodeJS.Platform;
  arch?: string;
  extract?: InstallIo["extract"];
}

/** Downloads Google's newest CLI into `root/<version>` and makes it the one Relay runs. */
export async function installAntigravity(
  root: string,
  fetch: Fetch,
  { version, platform, arch, extract }: InstallOptions = {},
): Promise<InstalledAntigravity> {
  const release = await latestAntigravity(fetch, platform, arch);
  if (version && version !== release.version)
    throw new Error(
      `Antigravity ${version} isn't offered any more; ${release.version} is.`,
    );
  const found = await installInto(root, release, { fetch, extract, platform });
  return { version: found.version, command: found.command, args: found.args };
}

let setup: { root: string; fetch: Fetch } | undefined;

/** Where Antigravity goes on this machine; set once by the app. */
export function configureAntigravity(next: { root: string; fetch: Fetch }) {
  setup = next;
}

function antigravitySetup() {
  if (!setup) throw new Error("Antigravity isn't set up in this process.");
  return setup;
}

export const currentAntigravity = () =>
  installedAntigravity(antigravitySetup().root);

/** The CLI on disk, downloaded first if there isn't one. */
export async function ensureAntigravity() {
  const { root, fetch } = antigravitySetup();
  return (await installedAntigravity(root)) ?? installAntigravity(root, fetch);
}

export const newestAntigravity = async () =>
  (await latestAntigravity(antigravitySetup().fetch)).version;

export function updateAntigravity(version?: string) {
  const { root, fetch } = antigravitySetup();
  return installAntigravity(root, fetch, { version });
}
