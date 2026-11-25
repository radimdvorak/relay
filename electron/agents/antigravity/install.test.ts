import { createHash } from "node:crypto";
import { mkdir, mkdtemp, readdir, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  cliTarget,
  installAntigravity,
  installedAntigravity,
  releaseFrom,
} from "./install";

const bucket =
  "https://storage.googleapis.com/antigravity-public/antigravity-cli";
const tarball = (version: string) =>
  `${bucket}/${version}-5250855658717184/darwin-arm/cli_mac_arm64.tar.gz`;
const sha512 = (text: string) =>
  createHash("sha512").update(text).digest("hex");
const manifest = (version: string, body = `cli ${version}`) => ({
  version,
  url: tarball(version),
  sha512: sha512(body),
});

describe("Antigravity's CLI releases", () => {
  it("names this machine the way Google's manifests do", () => {
    expect(cliTarget("darwin", "arm64")).toBe("darwin_arm64");
    expect(cliTarget("win32", "x64")).toBe("windows_amd64");
    expect(cliTarget("linux", "x64", true)).toBe("linux_amd64_musl");
    expect(cliTarget("linux", "riscv64")).toBeUndefined();
  });

  it("reads Google's release, and runs the CLI out of it", () => {
    expect(releaseFrom(manifest("1.5.16"))).toMatchObject({
      version: "1.5.16",
      archive: tarball("1.5.16"),
      cmd: "antigravity",
    });
    const windows = {
      ...manifest("1.5.16"),
      url: `${bucket}/1.5.16-1/windows-x64/cli_windows_x64.exe`,
    };
    expect(releaseFrom(windows, "win32").cmd).toBe("agy.exe");
  });

  it("won't download from anywhere but Google's bucket", () => {
    const elsewhere = {
      ...manifest("1.5.16"),
      url: "https://example.com/cli.tar.gz",
    };
    expect(() => releaseFrom(elsewhere)).toThrow(/Refusing to download/);
    expect(() => releaseFrom(manifest("1.5.16"), "win32")).toThrow(
      /Refusing to download/,
    );
  });
});

describe("installing Antigravity", () => {
  /** Google's manifest at `version`, and a tarball whose "unpacking" writes the CLI. */
  function google(state: { version: string; body?: string; fail?: boolean }) {
    const downloads: string[] = [];
    const fetch = async (url: string) => {
      if (url.endsWith("/darwin_arm64.json"))
        return Response.json(manifest(state.version));
      downloads.push(url);
      return state.fail
        ? new Response("gone", { status: 404 })
        : new Response(state.body ?? `cli ${state.version}`);
    };
    return { fetch, downloads };
  }

  const extract = async (_tarball: string, out: string) => {
    await mkdir(out, { recursive: true });
    await writeFile(join(out, "antigravity"), "cli");
  };

  const mac = { platform: "darwin" as const, arch: "arm64", extract };

  it("unpacks the newest release and runs it from there, keeping the one before", async () => {
    const root = await mkdtemp(join(tmpdir(), "relay-agy-"));
    await installAntigravity(root, google({ version: "1.5.13" }).fetch, mac);
    expect(await installedAntigravity(root)).toEqual({
      version: "1.5.13",
      command: join(root, "1.5.13", "antigravity"),
      args: [],
    });

    for (const version of ["1.5.15", "1.5.16"])
      await installAntigravity(root, google({ version }).fetch, mac);
    expect((await installedAntigravity(root))?.version).toBe("1.5.16");
    expect((await readdir(root)).sort()).toEqual([
      "1.5.15",
      "1.5.16",
      "current.json",
    ]);
  });

  it("replaces the old ACP server Relay used to download, even at the same version", async () => {
    const root = await mkdtemp(join(tmpdir(), "relay-agy-"));
    await mkdir(join(root, "1.5.2"));
    await writeFile(join(root, "1.5.2", "agy_acp_server.par"), "server");
    await writeFile(
      join(root, "current.json"),
      JSON.stringify({
        version: "1.5.2",
        cmd: "./agy_acp_server.par",
        args: [],
      }),
    );
    expect(await installedAntigravity(root)).toBeUndefined();

    await installAntigravity(root, google({ version: "1.5.2" }).fetch, mac);
    expect(await installedAntigravity(root)).toEqual({
      version: "1.5.2",
      command: join(root, "1.5.2", "antigravity"),
      args: [],
    });
  });

  it("throws away a download that doesn't match Google's checksum", async () => {
    const root = await mkdtemp(join(tmpdir(), "relay-agy-"));
    await installAntigravity(root, google({ version: "1.5.13" }).fetch, mac);
    const tampered = google({ version: "1.5.16", body: "something else" });
    await expect(installAntigravity(root, tampered.fetch, mac)).rejects.toThrow(
      /doesn't match its published checksum/,
    );
    expect((await installedAntigravity(root))?.version).toBe("1.5.13");
  });

  it("keeps the installed one when a download fails", async () => {
    const root = await mkdtemp(join(tmpdir(), "relay-agy-"));
    await installAntigravity(root, google({ version: "1.5.13" }).fetch, mac);
    await expect(
      installAntigravity(
        root,
        google({ version: "1.5.16", fail: true }).fetch,
        mac,
      ),
    ).rejects.toThrow(/failed \(404\)/);
    expect(await readdir(root)).toEqual(["1.5.13", "current.json"]);
  });

  it("won't install a version Google no longer offers", async () => {
    const root = await mkdtemp(join(tmpdir(), "relay-agy-"));
    const { fetch, downloads } = google({ version: "1.5.16" });
    await expect(
      installAntigravity(root, fetch, { ...mac, version: "1.5.13" }),
    ).rejects.toThrow(/isn't offered any more/);
    expect(downloads).toEqual([]);
  });
});
