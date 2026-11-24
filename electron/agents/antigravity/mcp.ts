import { z } from "zod";
import type { AcpToolCall } from "../acp/protocol";

const proxiedSchema = z.object({
  ServerName: z.string(),
  ToolName: z.string(),
});

/**
 * The MCP tool a call is for. Antigravity names it `mcp_<server>_<tool>`, or
 * passes it to `call_mcp_tool`. Only the call's own name says so: any tool's
 * arguments may hold a `ServerName`. A server with an underscore in its name
 * can't be told from its tool, so the first one ends the server's.
 */
export function antigravityMcp(call: Pick<AcpToolCall, "name" | "rawInput">) {
  if (call.name === "call_mcp_tool") {
    const proxied = proxiedSchema.safeParse(call.rawInput).data;
    return proxied && { server: proxied.ServerName, tool: proxied.ToolName };
  }
  const named = /^mcp_([^_]+)_(.+)$/.exec(call.name ?? "");
  return named ? { server: named[1]!, tool: named[2]! } : undefined;
}
