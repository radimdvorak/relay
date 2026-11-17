import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { memoByKey, memoOnce, memoWhileStamp } from "./memo";

beforeEach(() => vi.useFakeTimers());

afterEach(() => vi.useRealTimers());

describe("memoByKey", () => {
  it("answers a key from memory until the time is up", async () => {
    const load = vi.fn(async (key: string) => key + "!");
    const get = memoByKey(load, { ttl: 1000 });
    expect(await get("a")).toBe("a!");
    expect(await get("a")).toBe("a!");
    expect(load).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(1001);
    await get("a");
    expect(load).toHaveBeenCalledTimes(2);
  });

  it("asks again after a failure", async () => {
    const load = vi
      .fn<(key: string) => Promise<string>>()
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValue("ok");
    const get = memoByKey(load);
    await expect(get("a")).rejects.toThrow("offline");
    expect(await get("a")).toBe("ok");
  });

  it("keeps an answer asked for while an older one was failing", async () => {
    let fail!: (e: Error) => void;
    const load = vi
      .fn<(key: string) => Promise<string>>()
      .mockReturnValueOnce(new Promise((_, reject) => (fail = reject)))
      .mockResolvedValue("fresh");
    const get = memoByKey(load);
    const old = get("a");
    get.clear();
    expect(await get("a")).toBe("fresh");
    fail(new Error("offline"));
    await expect(old).rejects.toThrow("offline");
    expect(await get("a")).toBe("fresh");
    expect(load).toHaveBeenCalledTimes(2);
  });

  it("drops the oldest key past the limit", async () => {
    const load = vi.fn(async (key: string) => key);
    const get = memoByKey(load, { max: 2 });
    await get("a");
    await get("b");
    await get("c");
    await get("b");
    expect(load).toHaveBeenCalledTimes(3);
    await get("a");
    expect(load).toHaveBeenCalledTimes(4);
  });
});

describe("memoOnce", () => {
  it("keeps one answer, forgets on request and on failure", async () => {
    const load = vi
      .fn<() => Promise<number>>()
      .mockResolvedValueOnce(1)
      .mockResolvedValueOnce(2)
      .mockRejectedValueOnce(new Error("no"))
      .mockResolvedValue(4);
    const get = memoOnce(load);
    expect(await get()).toBe(1);
    expect(await get()).toBe(1);
    get.forget();
    expect(await get()).toBe(2);
    get.forget();
    await expect(get()).rejects.toThrow("no");
    expect(await get()).toBe(4);
  });
});

describe("memoWhileStamp", () => {
  it("asks again once the stamp changes or the time is up", async () => {
    let stamp = "v1";
    const load = vi.fn(async () => stamp);
    const get = memoWhileStamp(async () => stamp, load, 1000);
    expect(await get()).toBe("v1");
    vi.advanceTimersByTime(999);
    expect(await get()).toBe("v1");
    expect(load).toHaveBeenCalledTimes(1);
    stamp = "v2";
    expect(await get()).toBe("v2");
    vi.advanceTimersByTime(1000);
    await get();
    expect(load).toHaveBeenCalledTimes(3);
  });
});
