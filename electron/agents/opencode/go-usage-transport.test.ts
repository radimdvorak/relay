import { execFile } from "node:child_process";
import {
  createServer,
  get,
  type IncomingMessage,
  type Server,
  type ServerResponse,
} from "node:http";
import type { AddressInfo } from "node:net";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { build } from "esbuild";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readOpenCodeUsage } from "./go-usage";
import { runningOpenCodeService } from "./service-snapshot";

// The transport under the reader, with nothing of it mocked: OpenCode's
// registration file in a folder of its own, real HTTP services on loopback,
// and the real HTTP client. A spy only records what is sent. No key is used.

vi.mock("node:http", async (original) => {
  const http = await original<typeof import("node:http")>();
  return { ...http, get: vi.fn(http.get) };
});

const PASSWORD = "first-password";
const requests = vi.mocked(get);

const basic = (password: string) =>
  `Basic ${Buffer.from(`opencode:${password}`).toString("base64")}`;

const go = {
  rolling: {
    percent: 30,
    limited: false,
    resetsAt: Date.UTC(2026, 10, 22, 15),
  },
  weekly: { percent: 62, limited: false, resetsAt: Date.UTC(2026, 10, 23) },
  monthly: {
    percent: 49,
    limited: false,
    resetsAt: Date.UTC(2026, 11, 17, 21, 25, 51),
  },
};

const limits = {
  location: { directory: "/home" },
  data: { workspace: { id: "wrk_1" }, budgets: [], go },
};

type Hit = { path: string; authorization?: string };

type Behavior = {
  /** How /api/info answers; the service's own account of itself by default. */
  info?: (res: ServerResponse) => void | Promise<void>;
  /** How the limits route answers. */
  limits?: (res: ServerResponse) => void | Promise<void>;
};

type Listener = { url: string; hits: Hit[] };

let state: string;
let servers: Server[] = [];

const json = (res: ServerResponse, body: unknown, status = 200) => {
  res.statusCode = status;
  res.end(typeof body === "string" ? body : JSON.stringify(body));
};

const itself = { version: "2.3.2", pid: process.pid };

/** A service on loopback that wants `password` and answers as OpenCode 2 does. */
async function listen(
  behavior: Behavior = {},
  password = PASSWORD,
): Promise<Listener> {
  const hits: Hit[] = [];
  const server = createServer((req: IncomingMessage, res: ServerResponse) => {
    const path = new URL(req.url ?? "/", "http://service.test").pathname;
    hits.push({ path, authorization: req.headers.authorization });
    if (req.headers.authorization !== basic(password)) {
      return json(res, {}, 401);
    }
    if (path === "/api/info") {
      return void (behavior.info ?? ((r) => json(r, itself)))(res);
    }
    if (path === "/api/experimental/usage/limits") {
      return void (behavior.limits ?? ((r) => json(r, limits)))(res);
    }
    json(res, {}, 404);
  });
  servers.push(server);
  const port = await new Promise<number>((resolve) =>
    server.listen(0, "127.0.0.1", () =>
      resolve((server.address() as AddressInfo).port),
    ),
  );
  return { url: `http://127.0.0.1:${port}`, hits };
}

async function register(
  url: string,
  extra: Record<string, unknown> = {},
  /** The password the registration holds; null for none. */
  password: string | null = PASSWORD,
) {
  await mkdir(join(state, "opencode"), { recursive: true });
  await writeFile(
    join(state, "opencode", "service.json"),
    JSON.stringify({
      id: "service",
      version: "2.3.2",
      url,
      pid: process.pid,
      password: password ?? undefined,
      ...extra,
    }),
  );
}

const closeAll = () =>
  Promise.all(
    servers.map(
      (server) =>
        new Promise<void>((resolve) => {
          server.closeAllConnections();
          server.close(() => resolve());
        }),
    ),
  );

beforeEach(async () => {
  state = await mkdtemp(join(tmpdir(), "opencode-transport-"));
  vi.stubEnv("XDG_STATE_HOME", state);
  requests.mockClear();
});

afterEach(async () => {
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
  await closeAll();
  servers = [];
  await rm(state, { recursive: true, force: true });
});

const waiting = {
  provider: "opencode",
  windows: [],
  message: "Shows while OpenCode is running",
};

describe("a service on loopback with a password", () => {
  it("is found with the password it registered, which its health check and its limits are asked with", async () => {
    const service = await listen();
    await register(service.url);
    const found = await runningOpenCodeService();
    expect(found?.url.origin).toBe(new URL(service.url).origin);
    expect(found?.authorization).toBe(basic(PASSWORD));
    const usage = await readOpenCodeUsage();
    expect(usage.windows.map((window) => window.kind)).toEqual([
      "session",
      "weekly",
      "monthly",
    ]);
    expect(service.hits.map((hit) => hit.path)).toEqual([
      "/api/info",
      "/api/info",
      "/api/experimental/usage/limits",
    ]);
    for (const hit of service.hits) {
      expect(hit.authorization).toBe(basic(PASSWORD));
    }
  });

  it("is not found with a password it wasn't registered with", async () => {
    const service = await listen({}, "another-password");
    await register(service.url);
    expect(await runningOpenCodeService()).toBeUndefined();
  });

  it("keeps localhost on loopback", async () => {
    const service = await listen();
    await register(service.url.replace("127.0.0.1", "localhost"));
    expect((await readOpenCodeUsage()).windows).toHaveLength(3);
    expect(service.hits).toHaveLength(2);
  });

  it("is found without a password when none is registered and none is wanted", async () => {
    const hits: Hit[] = [];
    const bare = createServer((req, res) => {
      hits.push({
        path: req.url ?? "",
        authorization: req.headers.authorization,
      });
      json(res, itself);
    });
    servers.push(bare);
    const port = await new Promise<number>((resolve) =>
      bare.listen(0, "127.0.0.1", () =>
        resolve((bare.address() as AddressInfo).port),
      ),
    );
    await register(`http://127.0.0.1:${port}`, {}, null);
    const found = await runningOpenCodeService();
    expect(found?.authorization).toBeUndefined();
    expect(hits).toEqual([{ path: "/api/info", authorization: undefined }]);
  });
});

describe("an address that isn't a local HTTP service", () => {
  it.each([
    "https://127.0.0.1:4096",
    "http://example.com:4096",
    "http://user:secret@127.0.0.1:4096",
    "http://192.0.2.1:4096",
    "ftp://127.0.0.1",
    "not a url",
  ])("is refused before anything is sent to %s", async (url) => {
    await register(url);
    await expect(runningOpenCodeService()).rejects.toThrow(
      "OpenCode didn't report",
    );
    await expect(readOpenCodeUsage()).rejects.toThrow("OpenCode didn't report");
    expect(requests).not.toHaveBeenCalled();
  });
});

describe("a redirect", () => {
  it("from the health check isn't followed, so the password goes nowhere else", async () => {
    const elsewhere = await listen();
    const service = await listen({
      info: (res) => {
        res.statusCode = 302;
        res.setHeader("location", `${elsewhere.url}/api/info`);
        res.end();
      },
    });
    await register(service.url);
    expect(await runningOpenCodeService()).toBeUndefined();
    expect(await readOpenCodeUsage()).toMatchObject(waiting);
    expect(elsewhere.hits).toEqual([]);
    // The service itself got its own password and nothing asked of it after.
    expect(service.hits.every((hit) => hit.path === "/api/info")).toBe(true);
  });

  it("from the limits route isn't followed either", async () => {
    const elsewhere = await listen();
    const service = await listen({
      limits: (res) => {
        res.statusCode = 307;
        res.setHeader("location", `${elsewhere.url}/steal`);
        res.end();
      },
    });
    await register(service.url);
    await expect(readOpenCodeUsage()).rejects.toThrow("307");
    expect(elsewhere.hits).toEqual([]);
  });
});

describe("a health response that isn't the registered service's", () => {
  const bad: [string, (res: ServerResponse) => void][] = [
    ["404 (an OpenCode without the route)", (res) => json(res, {}, 404)],
    ["500 (failed)", (res) => json(res, itself, 500)],
    ["503 (still starting)", (res) => json(res, itself, 503)],
    ["text", (res) => json(res, "not json")],
    ["an empty body", (res) => json(res, "")],
    ["null", (res) => json(res, "null")],
    ["an empty object", (res) => json(res, {})],
    ["a version that isn't text", (res) => json(res, { version: 2, pid: 1 })],
    ["an empty version", (res) => json(res, { ...itself, version: "" })],
    ["a pid that isn't whole", (res) => json(res, { ...itself, pid: 1.5 })],
    [
      "another process",
      (res) => json(res, { ...itself, pid: process.pid + 1 }),
    ],
    ["another version", (res) => json(res, { ...itself, version: "2.0.0" })],
  ];

  it.each(bad)("is no service: %s", async (_name, info) => {
    const service = await listen({ info });
    await register(service.url);
    expect(await runningOpenCodeService()).toBeUndefined();
    expect(await readOpenCodeUsage()).toMatchObject(waiting);
    // Its limits are never asked for.
    expect(service.hits.filter((hit) => hit.path.includes("usage"))).toEqual(
      [],
    );
  });

  it("is no service when the registration names another version or process", async () => {
    const service = await listen();
    await register(service.url, { version: "2.0.0" });
    expect(await runningOpenCodeService()).toBeUndefined();
    await register(service.url, { pid: process.pid + 1 });
    expect(await runningOpenCodeService()).toBeUndefined();
    // Without a version registered, the health check's isn't held to one.
    await register(service.url, { version: undefined });
    expect(await runningOpenCodeService()).toBeDefined();
  });

  it.each(["", " \t "])(
    "rejects the blank registered version %j",
    async (version) => {
      const service = await listen();
      await register(service.url, { version });
      expect(await readOpenCodeUsage()).toMatchObject(waiting);
      expect(requests).not.toHaveBeenCalled();
    },
  );
});

describe("a service that isn't running", () => {
  it("isn't asked for anything without a registration", async () => {
    const service = await listen();
    expect(service.url).toMatch(/^http:/);
    expect(await runningOpenCodeService()).toBeUndefined();
    expect(await readOpenCodeUsage()).toMatchObject(waiting);
    expect(requests).not.toHaveBeenCalled();
    expect(service.hits).toEqual([]);
  });

  it("isn't found at a registration it has left behind", async () => {
    const service = await listen();
    await register(service.url);
    await closeAll();
    servers = [];
    expect(await runningOpenCodeService()).toBeUndefined();
    expect(await readOpenCodeUsage()).toMatchObject(waiting);
  });

  it("isn't found at an unreadable registration", async () => {
    await mkdir(join(state, "opencode"), { recursive: true });
    for (const text of ["", "not json", "{}", '{"url":7,"pid":1}']) {
      await writeFile(join(state, "opencode", "service.json"), text);
      expect(await runningOpenCodeService()).toBeUndefined();
    }
    expect(requests).not.toHaveBeenCalled();
  });
});

describe("how long a reading waits", () => {
  it.each(["info", "limits"] as const)(
    "aborts an unfinished %s response body",
    async (phase) => {
      const sent = Promise.withResolvers<void>();
      const closed = Promise.withResolvers<void>();
      const service = await listen({
        [phase]: (res: ServerResponse) => {
          res.once("close", closed.resolve);
          res.writeHead(200, { "content-type": "application/json" });
          res.write("{");
          sent.resolve();
        },
      });
      const controller = new AbortController();
      const timeout = vi.spyOn(AbortSignal, "timeout");
      if (phase === "limits")
        timeout.mockReturnValueOnce(new AbortController().signal);
      timeout.mockReturnValueOnce(controller.signal);
      await register(service.url);
      const reading = readOpenCodeUsage();
      const checked =
        phase === "info"
          ? expect(reading).resolves.toMatchObject(waiting)
          : expect(reading).rejects.toMatchObject({ code: "ABORT_ERR" });
      await sent.promise;
      // Wait until the client starts consuming the partial body.
      const request = requests.mock.results.at(-1)!.value;
      await new Promise<void>((resolve) => {
        request.once("response", (response: IncomingMessage) =>
          response.once("data", () => resolve()),
        );
      });
      controller.abort();
      await Promise.all([checked, closed.promise]);
      expect(timeout.mock.calls).toEqual(
        phase === "info" ? [[2_000]] : [[2_000], [20_000]],
      );
    },
  );
});

describe("a registration that changes while a reading is under way", () => {
  it("doesn't move the reading: its address, password and identity are one snapshot", async () => {
    const other = await listen({}, "second-password");
    const service = await listen({
      info: async (res) => {
        // OpenCode restarts as the health check is answered.
        await register(other.url, {}, "second-password");
        json(res, itself);
      },
    });
    await register(service.url);
    const usage = await readOpenCodeUsage();
    expect(usage.windows).toHaveLength(3);
    const limitsHit = service.hits.find((hit) => hit.path.includes("usage"));
    expect(limitsHit?.authorization).toBe(basic(PASSWORD));
    // The new registration is read by the next reading, not this one.
    expect(other.hits).toEqual([]);
    expect(
      requests.mock.calls.map(([url]) => new URL(String(url)).origin),
    ).toEqual([new URL(service.url).origin, new URL(service.url).origin]);
    const next = await runningOpenCodeService();
    expect(next?.url.origin).toBe(new URL(other.url).origin);
    expect(next?.authorization).toBe(basic("second-password"));
  });
});

it.skipIf(!process.allowedNodeEnvironmentFlags.has("--use-env-proxy"))(
  "keeps both requests off the proxy even when Node routes global requests through it",
  async () => {
    const service = await listen();
    await register(service.url);
    const proxied: { target?: string; authorized: boolean }[] = [];
    const proxy = createServer((req, res) => {
      proxied.push({
        target: req.url,
        authorized: !!req.headers.authorization,
      });
      res.writeHead(502).end();
    });
    proxy.on("connect", (req, socket) => {
      proxied.push({
        target: req.url,
        authorized: !!req.headers.authorization,
      });
      socket.end("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n");
    });
    servers.push(proxy);
    await new Promise<void>((resolve) => proxy.listen(0, "127.0.0.1", resolve));
    const authority = `127.0.0.1:${(proxy.address() as AddressInfo).port}`;
    const settings = Object.fromEntries(
      Object.entries(process.env).filter(
        ([name]) =>
          !/^(?:https?_proxy|all_proxy|no_proxy|node_use_env_proxy|node_options)$/i.test(
            name,
          ),
      ),
    );
    const bundle = await build({
      stdin: {
        contents: `
          import { readOpenCodeUsage } from "./electron/agents/opencode/go-usage";
          void (async () => {
            await fetch(process.env.RELAY_TEST_PROXY).catch(() => {});
            const usage = await readOpenCodeUsage();
            console.log(usage.windows.length);
          })().catch((error) => {
            console.error(error);
            process.exitCode = 1;
          });
        `,
        resolveDir: process.cwd(),
      },
      bundle: true,
      format: "cjs",
      packages: "external",
      platform: "node",
      write: false,
    });
    // The global fetch above proves the proxy is active independently of the reader.
    const proxyUrl = `http://${authority}`;
    const stdout = await new Promise<string>((resolve, reject) => {
      const child = execFile(
        process.execPath,
        ["--use-env-proxy", "--input-type=commonjs", "-"],
        {
          env: {
            ...settings,
            HTTP_PROXY: proxyUrl,
            HTTPS_PROXY: proxyUrl,
            ALL_PROXY: proxyUrl,
            NO_PROXY: "",
            NODE_USE_ENV_PROXY: "1",
            RELAY_TEST_PROXY: proxyUrl,
          },
          timeout: 5_000,
        },
        (error, output) => {
          if (error) reject(error);
          else resolve(output);
        },
      );
      child.stdin?.on("error", reject);
      child.stdin?.end(bundle.outputFiles[0].text);
    });
    expect(stdout.trim()).toBe("3");
    expect(proxied).toEqual([{ target: authority, authorized: false }]);
    expect(service.hits).toEqual(
      ["/api/info", "/api/experimental/usage/limits"].map((path) => ({
        path,
        authorization: basic(PASSWORD),
      })),
    );
  },
  10_000,
);
