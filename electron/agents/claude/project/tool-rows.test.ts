import { expect, it, vi } from "vitest";
import type { ClaudeRunOptions } from "./config";
import { ToolRows } from "./tool-rows";

const sendResult = (id: string, text: string, isError = false) => [
  {
    type: "tool_result" as const,
    tool_use_id: id,
    content: [{ type: "text" as const, text }],
    is_error: isError,
  },
];

function rowsHearingSends() {
  const onPeerSend = vi.fn();
  const rows = new ToolRows({ onPeerSend } as unknown as ClaudeRunOptions);
  return { rows, onPeerSend };
}

it("reports a message to another session once it went out", () => {
  const { rows, onPeerSend } = rowsHearingSends();
  rows.call(
    {
      id: "t1",
      name: "SendMessage",
      input: { to: "mudtoy-d4", message: "Can I have Play?", summary: "ask" },
    },
    null,
  );
  expect(onPeerSend).not.toHaveBeenCalled();
  rows.results(sendResult("t1", '{"success":true,"msg_id":"x"}'), null);
  expect(onPeerSend).toHaveBeenCalledWith({
    to: "mudtoy-d4",
    message: "Can I have Play?",
  });
});

it("keeps quiet about a message that didn't go out", () => {
  const { rows, onPeerSend } = rowsHearingSends();
  const ask = { to: "gone-1", message: "hello" };
  rows.call({ id: "t1", name: "SendMessage", input: ask }, null);
  rows.results(sendResult("t1", '{"success":false,"message":"no such"}'), null);
  rows.call({ id: "t2", name: "SendMessage", input: ask }, null);
  rows.results(sendResult("t2", "Error: unknown recipient", true), null);
  // A subscription without a message tells nobody anything.
  rows.call(
    {
      id: "t3",
      name: "SendMessage",
      input: { to: "x", notify_when_idle: true },
    },
    null,
  );
  rows.results(sendResult("t3", '{"success":true}'), null);
  expect(onPeerSend).not.toHaveBeenCalled();
});
