import { z } from "zod";
import { agentProviderSchema } from "../../shared/agents";
import { agentRuntime } from "../agents";
import {
  computerIdSchema,
  remoteProjects,
  takes,
  type ApiContext,
  type Handlers,
} from "./context";

/** The models each agent offers, here or on a paired computer. */
export function agentModelHandlers(ctx: ApiContext) {
  const { projectChats } = ctx;
  return {
    agentModels: takes(
      [agentProviderSchema, computerIdSchema],
      (provider, computerId) =>
        // A project on a paired computer runs that computer's own copy.
        computerId
          ? remoteProjects(ctx).agentModels(computerId, provider)
          : agentRuntime(provider).models(),
    ),

    reloadAgentModels: takes(
      [agentProviderSchema, z.boolean(), computerIdSchema],
      async (provider, stopRunning, computerId) => {
        if (computerId)
          return remoteProjects(ctx).ask(
            computerId,
            "reloadAgentModels",
            provider,
            stopRunning,
          );
        const runtime = agentRuntime(provider);
        if (runtime.forgetModels) {
          runtime.forgetModels(); // nothing restarts, so no thread stops
          return { running: [] };
        }
        const running = projectChats.runningOn(provider);
        if (running.length && !stopRunning) return { running };
        let stopped: string[] = [];
        try {
          await runtime.reloadModels?.(async () => {
            stopped = await projectChats.stopRunningOn(provider);
          });
        } finally {
          await projectChats.resumeStopped(stopped);
        }
        return { running: [] };
      },
    ),
  } satisfies Handlers;
}
