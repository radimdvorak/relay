import { describe, expect, it } from "vitest";
import { firebaseRequest, pushMessageFrom, pushTitle } from "./push";

const valid = { token: "t", computer: "mac", chatId: "c1", kind: "finished" };

describe("pushMessageFrom", () => {
  it("takes a push with or without its title", () => {
    expect(pushMessageFrom(valid)).toEqual(valid);
    expect(pushMessageFrom({ ...valid, title: "Fix it" })).toEqual({
      ...valid,
      title: "Fix it",
    });
  });

  it("refuses anything that isn't one, so the service sends nothing else", () => {
    expect(pushMessageFrom(undefined)).toBeUndefined();
    expect(pushMessageFrom({ ...valid, kind: "deleted" })).toBeUndefined();
    expect(pushMessageFrom({ ...valid, token: "" })).toBeUndefined();
    expect(
      pushMessageFrom({ ...valid, title: "x".repeat(201) }),
    ).toBeUndefined();
    expect(pushMessageFrom({ ...valid, chatId: 5 })).toBeUndefined();
  });

  it("drops fields it doesn't know instead of passing them to Firebase", () => {
    const message = pushMessageFrom({ ...valid, notification: { body: "hi" } });

    expect(firebaseRequest(message!).message.data).toEqual({
      computer: "mac",
      chatId: "c1",
      kind: "finished",
    });
  });
});

describe("pushTitle", () => {
  it("cuts a long title without leaving half an emoji", () => {
    const title = pushTitle("a".repeat(198) + "😀😀");

    expect(title).toBe("a".repeat(198) + "…");
  });
});
