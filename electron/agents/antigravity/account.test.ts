import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, expect, it } from "vitest";
import { antigravityCredentialRevision } from "./account";

let home: string;

beforeEach(async () => {
  home = await mkdtemp(join(tmpdir(), "relay-agy-account-"));
  await mkdir(join(home, "antigravity-acp"));
});

afterEach(() => rm(home, { recursive: true, force: true }));

const save = (name: string, value: unknown) =>
  writeFile(join(home, "antigravity-acp", name), JSON.stringify(value));

const saveLogin = (refresh_token: string, access_token: string) =>
  save("antigravity-oauth-token", { token: { access_token, refresh_token } });

it("keeps its credential revision as the CLI renews the access token", async () => {
  await saveLogin("account-a", "first hour");
  const signedIn = await antigravityCredentialRevision(home);
  await saveLogin("account-a", "second hour");
  expect(await antigravityCredentialRevision(home)).toBe(signedIn);
});

it("changes its credential revision on signing in, as someone else, out, or another way", async () => {
  const revisions = [await antigravityCredentialRevision(home)];
  await saveLogin("account-a", "token");
  revisions.push(await antigravityCredentialRevision(home));
  await saveLogin("account-b", "token");
  revisions.push(await antigravityCredentialRevision(home));
  await save("settings.json", { auth: { type: "gemini-api-key" } });
  revisions.push(await antigravityCredentialRevision(home));
  expect(new Set(revisions).size).toBe(revisions.length);
});
