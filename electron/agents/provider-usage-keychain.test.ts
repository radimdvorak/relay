import { afterEach, expect, it, vi } from "vitest";
import { readProviderUsage } from "./provider-usage";

// No auth file anywhere: the Codex CLI keeps its sign-in in the Keychain.
vi.mock("node:fs/promises", () => ({
  stat: async () => {
    throw new Error("ENOENT");
  },
  readFile: async () => {
    throw new Error("ENOENT");
  },
}));

type Item = { service: string; account: string; value: string };
const keychain: Item[] = [];

/** `security` on a Keychain of `keychain`'s generic passwords. */
vi.mock("node:child_process", () => ({
  execFile: (
    _file: string,
    args: string[],
    _options: object,
    done: (error: Error | null, out?: { stdout: string }) => void,
  ) => {
    const flag = (name: string) => {
      const at = args.indexOf(name);
      return at < 0 ? undefined : args[at + 1];
    };
    const service = flag("-s");
    const account = flag("-a");
    const matches = (item: Item) =>
      item.service === service &&
      (account === undefined || item.account === account);
    if (args[0] === "find-generic-password") {
      const item = keychain.find(matches);
      if (!item) return done(new Error("not found"));
      if (args.includes("-w")) return done(null, { stdout: item.value });
      return done(null, {
        stdout: `class: "genp"\nattributes:\n    "acct"<blob>="${item.account}"\n    "svce"<blob>="${item.service}"\n`,
      });
    }
    // add-generic-password -U updates the item with this service and account.
    const value = flag("-w")!;
    const item = keychain.find(
      (i) => i.service === service && i.account === (account ?? ""),
    );
    if (item) item.value = value;
    else keychain.push({ service: service!, account: account ?? "", value });
    done(null, { stdout: "" });
  },
}));

const platform = process.platform;

afterEach(() => {
  Object.defineProperty(process, "platform", { value: platform });
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

const jwt = (exp: number) =>
  [
    "header",
    Buffer.from(JSON.stringify({ exp })).toString("base64url"),
    "sig",
  ].join(".");

it("keeps the tokens a refresh rotated in the Codex CLI's own Keychain item", async () => {
  Object.defineProperty(process, "platform", { value: "darwin" });
  vi.stubEnv("CODEX_HOME", "/nowhere/.codex");
  const expired = jwt(Math.floor(Date.now() / 1000) - 60);
  const fresh = jwt(Math.floor(Date.now() / 1000) + 3600);
  // Codex names the item's account after its home folder.
  keychain.push({
    service: "Codex Auth",
    account: "cli|0123456789abcdef",
    value: JSON.stringify({
      tokens: { access_token: expired, refresh_token: "spent-after-use" },
    }),
  });
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: URL) => {
      if (url.href === "https://auth.openai.com/oauth/token")
        return Response.json({ access_token: fresh, refresh_token: "next" });
      return Response.json({
        rate_limit: {
          primary_window: { used_percent: 12, limit_window_seconds: 18_000 },
        },
      });
    }),
  );

  const usage = await readProviderUsage("codex");

  // A second item would leave the CLI the spent refresh token, signing it out.
  expect(keychain).toHaveLength(1);
  expect(JSON.parse(keychain[0].value).tokens).toEqual({
    access_token: fresh,
    refresh_token: "next",
  });
  expect(usage.windows.map((w) => w.usedPercent)).toEqual([12]);
});
