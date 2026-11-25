import { afterEach, expect, it, vi } from "vitest";
import { createSignInRenewal } from "./renewal";
import type { QuotaCredential } from "./usage";

afterEach(() => vi.restoreAllMocks());

const minute = 60_000;

/** A saved sign-in on a clock, and a CLI whose renewals work or don't. */
function scenario() {
  vi.spyOn(console, "warn").mockImplementation(() => {});
  const state = { now: 0, works: false, who: "a" };
  const saved = (expiresAt: number): QuotaCredential => ({
    accessToken: "token",
    fingerprint: state.who,
    renewable: true,
    expiresAt,
  });
  let current = saved(-1);
  const renew = vi.fn(async () => {
    if (!state.works) throw new Error("offline");
    current = saved(state.now + 60 * minute);
  });
  const renewal = createSignInRenewal(
    renew,
    async () => current,
    () => state.now,
  );
  return { state, renew, renewal, expired: () => saved(-1) };
}

it("returns the sign-in the CLI renewed", async () => {
  const { state, renewal, expired } = scenario();
  state.works = true;
  expect((await renewal(expired()))?.expiresAt).toBe(60 * minute);
});

it("holds off longer after each failure, and starts over once one worked", async () => {
  const { state, renew, renewal, expired } = scenario();
  for (const wait of [1, 5, 15, 60, 60]) {
    renew.mockClear();
    await renewal(expired());
    state.now += wait * minute - 1;
    await renewal(expired());
    expect(renew).toHaveBeenCalledOnce();
    state.now += 1;
  }
  state.works = true;
  await renewal(expired());
  state.works = false;
  renew.mockClear();
  await renewal(expired());
  state.now += minute;
  await renewal(expired());
  expect(renew).toHaveBeenCalledTimes(2);
});

it("tries at once for someone who signed in since", async () => {
  const { state, renew, renewal, expired } = scenario();
  await renewal(expired());
  state.who = "b";
  await renewal(expired());
  expect(renew).toHaveBeenCalledTimes(2);
});

it("renews once for readings that come together", async () => {
  const { state, renew, renewal, expired } = scenario();
  state.works = true;
  await Promise.all([renewal(expired()), renewal(expired())]);
  expect(renew).toHaveBeenCalledOnce();
});

it("leaves a sign-in that can't be renewed alone", async () => {
  const { renew, renewal, expired } = scenario();
  const credential = { ...expired(), renewable: false };
  expect(await renewal(credential)).toBe(credential);
  expect(renew).not.toHaveBeenCalled();
});
