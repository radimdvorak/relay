import { describe, expect, it } from "vitest";
import {
  firebaseRefusal,
  firebaseRequest,
  pushMessageFrom,
  pushTitle,
} from "./push";

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

describe("firebaseRefusal", () => {
  const fcm = (code: number, details: unknown[]) =>
    JSON.stringify({ error: { code, details } });
  const errorCode = (code: string) => ({
    "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError",
    errorCode: code,
  });

  it("calls a token gone only when Firebase says it is", () => {
    expect(firebaseRefusal(404, fcm(404, [errorCode("UNREGISTERED")]))).toBe(
      "gone",
    );
    expect(
      firebaseRefusal(403, fcm(403, [errorCode("SENDER_ID_MISMATCH")])),
    ).toBe("gone");
    const badToken = {
      "@type": "type.googleapis.com/google.rpc.BadRequest",
      fieldViolations: [{ field: "message.token" }],
    };
    expect(
      firebaseRefusal(400, fcm(400, [errorCode("INVALID_ARGUMENT"), badToken])),
    ).toBe("gone");
  });

  it("keeps the token through a wrong project or a bad message", () => {
    expect(firebaseRefusal(404, "Not Found")).toBe("refused");
    expect(
      firebaseRefusal(400, fcm(400, [errorCode("INVALID_ARGUMENT")])),
    ).toBe("refused");
  });

  it("says a quota or an outage may pass", () => {
    expect(firebaseRefusal(429, fcm(429, [errorCode("QUOTA_EXCEEDED")]))).toBe(
      "busy",
    );
    expect(firebaseRefusal(503, "")).toBe("busy");
  });
});
