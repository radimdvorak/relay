import { expect, it } from "vitest";
import { wokenBy, type PeerMessage } from "./peer-messages";

const exhaust = { name: "mudtoy-d4", threadId: "exhaust" };

const arrived = (at: number, direction: PeerMessage["direction"] = "in") => [
  { id: "m", direction, peer: exhaust, text: "Play's yours", at },
];

it("names the session whose message came just before Claude started by itself", () => {
  expect(wokenBy(arrived(1000), 5000, 500)).toBe(exhaust);
  // The turn may start before Relay hears the message went out.
  expect(wokenBy(arrived(5000), 1000, 500)).toBe(exhaust);
});

it("doesn't credit a message read mid-answer, an old one, or one this thread sent", () => {
  expect(wokenBy(arrived(1000), 5000, 2000)).toBeUndefined();
  expect(wokenBy(arrived(1000), 120_000)).toBeUndefined();
  expect(wokenBy(arrived(1000, "out"), 2000)).toBeUndefined();
});
