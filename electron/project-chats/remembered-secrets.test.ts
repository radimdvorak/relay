import { expect, it, vi } from "vitest";
import type { Store } from "../app/store";
import { RememberedSecrets } from "./remembered-secrets";

const VALUE = "ghp_s3cr3t-value";

/** A store's saved state, and a credential store that seals by reversing. */
function fixture(canSeal = true) {
  const state = { version: 1, folders: {}, progress: {} } as ReturnType<
    Store["get"]
  >;
  const store = {
    get: () => state,
    update: async (change: (s: typeof state) => void) => change(state),
  } as unknown as Pick<Store, "get" | "update">;
  const reversed = (text: string) => [...text].reverse().join("");
  const secrets = new RememberedSecrets(
    store,
    async (value) => (canSeal ? reversed(value) : null),
    async (sealed) => reversed(sealed),
    async () => canSeal,
  );
  return { secrets, state };
}

it("gives a remembered secret back under its own name, however it is cased or spaced", async () => {
  const { secrets, state } = fixture();
  await secrets.remember("project", "npm publish token", VALUE);
  expect(await secrets.value("project", "NPM  publish Token ")).toBe(VALUE);
  expect(secrets.list("project")).toEqual([
    { name: "npm publish token", saved: expect.any(Number) },
  ]);
  // Sealed, so what is saved to disk never holds the value.
  expect(JSON.stringify(state)).not.toContain(VALUE);
});

it("never gives it to a lookalike name or another project", async () => {
  const { secrets } = fixture();
  await secrets.remember("project", "npm publish token", VALUE);
  for (const name of ["npm token", "npm publish tokens", "npm-publish-token"])
    expect(await secrets.value("project", name)).toBeUndefined();
  expect(await secrets.value("other", "npm publish token")).toBeUndefined();
});

it("forgets one secret and keeps the rest", async () => {
  const { secrets, state } = fixture();
  await secrets.remember("project", "npm publish token", VALUE);
  await secrets.remember("project", "Staging password", "hunter2");
  await secrets.forget("project", "NPM publish token");
  expect(await secrets.value("project", "npm publish token")).toBeUndefined();
  expect(secrets.list("project").map((s) => s.name)).toEqual([
    "Staging password",
  ]);
  expect(JSON.stringify(state)).not.toContain(
    VALUE.split("").reverse().join(""),
  );
});

it("replaces what was saved under the same name", async () => {
  const { secrets } = fixture();
  await secrets.remember("project", "npm publish token", "old");
  await secrets.remember("project", "Npm Publish Token", VALUE);
  expect(await secrets.value("project", "npm publish token")).toBe(VALUE);
  expect(secrets.list("project")).toHaveLength(1);
});

it("remembers until Relay quits where the computer can't seal it, and writes nothing", async () => {
  const { secrets, state } = fixture(false);
  await secrets.remember("project", "npm publish token", VALUE);
  expect(await secrets.value("project", "npm publish token")).toBe(VALUE);
  expect(secrets.list("project")).toEqual([
    { name: "npm publish token", saved: expect.any(Number), sessionOnly: true },
  ]);
  expect(state.projectSecrets).toBeUndefined();
  // The card and settings say so before anything is saved.
  expect((await fixture(false).secrets.saved("project")).sessionOnly).toBe(
    true,
  );
  expect(
    (await fixture().secrets.saved("project")).sessionOnly,
  ).toBeUndefined();
});

it("asks again when what was saved can't be read any more", async () => {
  const { secrets, state } = fixture();
  await secrets.remember("project", "npm publish token", VALUE);
  vi.spyOn(console, "warn").mockImplementation(() => {});
  const locked = new RememberedSecrets(
    { get: () => state, update: async () => {} } as never,
    async () => null,
    async () => {
      throw new Error("Keychain access denied");
    },
  );
  expect(await locked.value("project", "npm publish token")).toBeUndefined();
});
