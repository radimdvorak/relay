import { computerRouteSchema, type ComputerRoute } from "../../shared/handoff";
import { checkRoute, wslDistros } from "../handoff/pipe-route";
import { sshHosts } from "../handoff/ssh-hosts";
import { installOutput, installRelayOver } from "../handoff/install-over";
import { takes, type ApiContext, type Handlers } from "./context";

/** Reaching a computer over SSH or into WSL: checking the route, pairing over it, installing Relay there. */
export function computerRouteHandlers(ctx: ApiContext) {
  // A checkup's ssh or wsl.exe under way, per route, so Stop can end it.
  const checking = new Map<string, AbortController>();
  const stoppable = async <T>(
    route: ComputerRoute,
    work: (signal: AbortSignal) => Promise<T>,
  ) => {
    const key = JSON.stringify(route);
    checking.get(key)?.abort();
    const controller = new AbortController();
    checking.set(key, controller);
    try {
      return await work(controller.signal);
    } finally {
      if (checking.get(key) === controller) checking.delete(key);
    }
  };
  const computers = () => {
    const services = ctx.handoffs();
    if (!services) throw new Error("Relay is still starting.");
    return services.computers;
  };
  return {
    checkComputer: takes([computerRouteSchema], (route) =>
      stoppable(route, (signal) => checkRoute(route, undefined, signal)),
    ),
    connectComputer: takes([computerRouteSchema], (route) =>
      stoppable(route, (signal) => computers().pairOver(route, signal)),
    ),
    stopComputerCheck: takes([computerRouteSchema], (route) => {
      checking.get(JSON.stringify(route))?.abort();
    }),
    wslDistros: () => wslDistros(),
    sshHosts: () => sshHosts(),
    installRelayOver: takes([computerRouteSchema], (route) =>
      installRelayOver(route),
    ),
    relayInstallOutput: takes([computerRouteSchema], (route) =>
      installOutput(route),
    ),
  } satisfies Handlers;
}
