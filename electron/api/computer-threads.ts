import { chatTriageSchema } from "../../shared/projects";
import { idSchema } from "../../shared/validation";
import { takes, type ApiContext, type Handlers } from "./context";

/** The paired computers' own threads: listing them, and following one from here. */
export function computerThreadHandlers(ctx: ApiContext) {
  const threads = () => {
    const services = ctx.handoffs();
    if (!services) throw new Error("Relay is still starting.");
    return services.sender.threads;
  };
  return {
    computerThreads: () => threads().list(),
    openComputerThread: takes([idSchema, idSchema], (computerId, threadId) =>
      threads().open(computerId, threadId),
    ),
    triageComputerThread: takes(
      [idSchema, idSchema, chatTriageSchema],
      (computerId, threadId, triage) =>
        threads().triage(computerId, threadId, triage),
    ),
  } satisfies Handlers;
}
