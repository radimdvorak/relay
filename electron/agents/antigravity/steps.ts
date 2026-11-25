import { z } from "zod";
import type { AcpToolCall } from "../acp/protocol";

const stepsSchema = z.object({
  steps: z.array(z.object({ title: z.string(), status: z.string() })),
});

/** Antigravity's word for a step's state, as ACP's plans say it. */
const statuses: Record<string, string> = {
  done: "completed",
  in_progress: "in_progress",
  pending: "pending",
  failed: "failed",
};

/**
 * The task list an `update_steps` call carries, as an ACP plan's entries:
 * Antigravity keeps its list with a tool call instead of a plan update.
 */
export function antigravitySteps(call: Partial<AcpToolCall>) {
  if (call.name !== "update_steps") return undefined;
  const steps = stepsSchema.safeParse(call.rawInput).data?.steps ?? [];
  return steps
    .filter((step) => step.status !== "skipped")
    .map((step) => ({
      content: step.title,
      status: statuses[step.status] ?? "pending",
    }));
}
