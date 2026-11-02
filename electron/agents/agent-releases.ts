import type { AgentVersion } from "../../shared/agent-updates";
import type { Exec } from "../platform/executables";
import { releasedBy, releaseTimes, type ReleaseTimes } from "./npm-release-age";

/** How long a registry's answer is kept before asking again. */
const answerLifetime = 60 * 60 * 1000;

/** A Homebrew formula or cask, which `brew upgrade` updates. */
export interface BrewKeg {
  name: string;
  kind: "formula" | "cask";
  prefix: string;
}

export interface ReleasesIo {
  exec(file: string, args: string[], timeout: number): Promise<Exec>;
  fetch(url: string, init?: RequestInit): Promise<Response>;
  /** npm's `min-release-age` in days, 0 when unset. */
  npmReleaseAge?(): Promise<number>;
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null;

const fresher = (at: number, fresh: boolean) =>
  !fresh && Date.now() - at < answerLifetime;

/**
 * The newest release each installer offers, asked over the network and kept
 * for an hour; `fresh` asks again anyway.
 */
export class AgentReleases {
  private newestCache = new Map<string, { at: number; version?: string }>();
  private timesCache = new Map<string, { at: number; times: ReleaseTimes }>();

  constructor(
    private readonly io: ReleasesIo,
    private readonly probeTimeout: number,
  ) {}

  /** The version npm's `tag` points at for `pkg`. */
  async npm(pkg: string, tag: string, fresh: boolean) {
    return this.remembered(`${pkg}@${tag}`, fresh, () =>
      this.io
        .fetch(
          `https://registry.npmjs.org/-/package/${encodeURIComponent(pkg)}/dist-tags`,
          {
            headers: { accept: "application/json" },
            signal: AbortSignal.timeout(5000),
          },
        )
        .then((response) => (response.ok ? response.json() : undefined))
        .then((tags) => {
          const latest = isRecord(tags) ? tags[tag] : undefined;
          return typeof latest === "string" ? latest : undefined;
        })
        .catch(() => undefined),
    );
  }

  /** What `brew upgrade` would bring; Homebrew lags npm by hours. */
  async brew(brew: string, keg: BrewKeg) {
    const info = await this.io.exec(
      brew,
      ["info", "--json=v2", keg.name],
      this.probeTimeout,
    );
    if (info.code !== 0) return undefined;
    let parsed: unknown;
    try {
      parsed = JSON.parse(info.stdout);
    } catch {
      return undefined;
    }
    return brewVersionIn(parsed, keg.kind) || undefined;
  }

  /** What a downloaded agent's own source offers. */
  sdk(key: string, fresh: boolean, newest: () => Promise<string | undefined>) {
    return this.remembered(key, fresh, () => newest().catch(() => undefined));
  }

  /**
   * What npm installs when its `min-release-age` holds back `latest`: an
   * update would otherwise "finish" and leave the same version behind.
   */
  async aged(
    pkg: string | undefined,
    latest: string,
    fresh: boolean,
  ): Promise<Pick<AgentVersion, "latest" | "held">> {
    const days = (await this.io.npmReleaseAge?.().catch(() => 0)) ?? 0;
    if (!days || !pkg) return { latest };
    let cached = this.timesCache.get(pkg);
    if (!cached || !fresher(cached.at, fresh)) {
      const times = await this.io
        .fetch(`https://registry.npmjs.org/${encodeURIComponent(pkg)}`, {
          headers: { accept: "application/json" },
          signal: AbortSignal.timeout(15_000),
        })
        .then((response) => (response.ok ? response.json() : undefined))
        .then(releaseTimes)
        .catch((): ReleaseTimes => ({}));
      cached = { at: Date.now(), times };
      this.timesCache.set(pkg, cached);
    }
    const { version, held } = releasedBy(cached.times, latest, days);
    return { latest: version, ...(held && { held }) };
  }

  private async remembered(
    key: string,
    fresh: boolean,
    ask: () => Promise<string | undefined>,
  ) {
    const cached = this.newestCache.get(key);
    if (cached && fresher(cached.at, fresh)) return cached.version;
    const version = await ask();
    this.newestCache.set(key, { at: Date.now(), version });
    return version;
  }
}

/** The version `brew upgrade` would bring, out of `brew info --json=v2`. */
function brewVersionIn(info: unknown, kind: BrewKeg["kind"]) {
  const list = isRecord(info)
    ? info[kind === "formula" ? "formulae" : "casks"]
    : undefined;
  const entry = isRecord(list) ? list[0] : undefined;
  if (!isRecord(entry)) return undefined;
  if (kind === "formula") {
    const stable = isRecord(entry.versions) ? entry.versions.stable : undefined;
    return typeof stable === "string" ? stable : undefined;
  }
  // A cask's version may carry a build after a comma, e.g. `1.4.2,8812`.
  if (typeof entry.version !== "string") return undefined;
  const comma = entry.version.indexOf(",");
  return comma < 0 ? entry.version : entry.version.slice(0, comma);
}
