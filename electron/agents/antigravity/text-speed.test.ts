import { expect, it } from "vitest";
import { TextSpeed } from "./text-speed";

function timed() {
  let now = 0;
  const speed = new TextSpeed(() => now);
  /** Text of `length` characters, `after` ms since the last thing. */
  const text = (length: number, after: number) => {
    now += after;
    speed.text("x".repeat(length));
  };
  const call = (args: number, after: number, name = "write_file") => {
    now += after;
    speed.call({ toolCallId: `call-${now}`, name, rawInput: "x".repeat(args) });
  };
  return { speed, text, call };
}

it("times text from its first piece to its last, over what came between", () => {
  const { speed, text } = timed();
  text(100, 20_000);
  text(100, 500);
  text(200, 500);
  // 300 of the 400 characters came in that second.
  expect(speed.finish(400)).toEqual({ output: 300, ms: 1000 });
});

it("leaves out thinking, tools and the arguments of their calls", () => {
  const { speed, text, call } = timed();
  text(10, 0);
  text(90, 400);
  call(98, 5000); // 100 characters as JSON
  text(10, 60_000);
  text(40, 200);
  speed.pause();
  text(10, 30_000);
  text(40, 400);
  expect(speed.finish(300)).toEqual({ output: 170, ms: 1000 });
});

it("doesn't time what it can't: text in one piece, a burst, or a prompt with subagents", () => {
  const { speed, text, call } = timed();
  text(500, 3000);
  expect(speed.finish(100)).toBeUndefined();

  text(500, 0);
  text(500, 1);
  expect(speed.finish(200)).toBeUndefined();

  call(10, 0, "invoke_subagent");
  text(100, 100);
  text(100, 1000);
  expect(speed.finish(200)).toBeUndefined();

  // The next prompt starts afresh.
  text(100, 100);
  text(100, 1000);
  expect(speed.finish(200)).toEqual({ output: 100, ms: 1000 });
});
