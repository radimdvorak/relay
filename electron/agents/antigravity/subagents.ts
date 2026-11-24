import { z } from "zod";
import type { AcpToolCall } from "../acp/protocol";
import { acpActivity } from "../acp/activity";
import { SubagentBooks, type SubagentBook } from "../subagent-book";

export const antigravitySubagents = new SubagentBooks();

const startSchema = z.object({
  Role: z.string().optional().catch(undefined),
  Prompt: z.string().optional().catch(undefined),
});

/** Google's own tool; an MCP server's is named `mcp_<server>_<tool>`. */
const startsSubagent = (call: Partial<AcpToolCall>) =>
  call.name === "invoke_subagent";

/** ACP says a subagent was started, not what it does after or how to stop it. */
export class AntigravitySubagents {
  private ids = new Set<string>();
  constructor(private book: SubagentBook) {}

  update(call: Partial<AcpToolCall> & { toolCallId: string }) {
    if (!this.ids.has(call.toolCallId)) {
      if (!startsSubagent(call)) return;
      this.ids.add(call.toolCallId);
      const start = startSchema.safeParse(call.rawInput).data;
      this.book.start(call.toolCallId, {
        description: start?.Role || "Antigravity subagent",
        brief: start?.Prompt,
        canStop: false,
      });
    }
    const output =
      typeof call.rawOutput === "string" ? call.rawOutput : undefined;
    this.book.call(call.toolCallId, acpActivity(call));
    if (output) this.book.report(call.toolCallId, output);
    if (call.status === "failed")
      this.book.end(call.toolCallId, "failed", output);
    else if (call.status === "completed")
      this.book.end(
        call.toolCallId,
        "idle",
        output ??
          "Started. Antigravity doesn't report what it does after that.",
      );
  }

  settle(stopped: boolean) {
    for (const id of this.ids)
      if (this.book.detail(id)?.status === "running")
        this.book.end(
          id,
          stopped ? "stopped" : "idle",
          stopped
            ? undefined
            : "Antigravity stopped reporting on this subagent.",
        );
  }
}
