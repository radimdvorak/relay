import { expect, it } from "vitest";
import { alwaysExplained, secretDestination } from "./secret-request";

// The phones say the same words: SecretRequestsTest.kt and SecretRequestsTests.swift.
it("says what Always does, what is already saved and how long it lasts", () => {
  const base =
    "Always also keeps it for this project: any agent here that asks for “npm token” by exactly that name gets it without a card.";
  expect(alwaysExplained("npm token", { secrets: [] })).toBe(base);
  expect(
    alwaysExplained("npm token", {
      secrets: [{ name: "npm publish token" }, { name: "Staging password" }],
    }),
  ).toBe(
    `${base} Already saved here under other names: npm publish token, Staging password.`,
  );
  // Saved from another thread while this card was open: not another name.
  expect(
    alwaysExplained("npm token", {
      secrets: [{ name: " NPM  token" }, { name: "Staging password" }],
    }),
  ).toBe(`${base} Already saved here under other names: Staging password.`);
  expect(
    alwaysExplained("npm token", {
      secrets: [{ name: "npm publish token" }],
      sessionOnly: true,
    }),
  ).toBe(
    `${base} This computer has no credential store, so it is kept only until Relay quits. Already saved here under other names: npm publish token.`,
  );
});

it("names the paired computer that keeps it", () => {
  expect(
    alwaysExplained("npm token", {
      secrets: [{ name: "npm publish token" }],
      sessionOnly: true,
      computer: "Workshop",
    }),
  ).toBe(
    "Always also keeps it on Workshop for this project: any agent here that asks for “npm token” by exactly that name gets it without a card. Workshop has no credential store, so it is kept only until Relay quits there. Already saved there under other names: npm publish token.",
  );
  expect(secretDestination("Claude", "Workshop")).toBe(
    "It goes to a private file on Workshop that Claude's commands read, not into the chat. Relay deletes the file after 24 hours.",
  );
});

it("tells where the value goes", () => {
  expect(secretDestination("Claude")).toBe(
    "It goes to a private file that Claude's commands read, not into the chat. Relay deletes the file after 24 hours.",
  );
});
