// What an ACP agent asks Relay while a turn runs: to allow a tool, or to fill
// in a form. Nothing else is offered to it.
import type { AgentOptions } from "../types";
import { AcpRequestError } from "./connection";
import { answerElicitation } from "./elicitation";
import { permission } from "./permission";
import type { AcpProfile } from "./profiles";
import type { AcpTurnEvents } from "./turn-events";

/** Answers the running turn's requests; `signal` ends with the turn. */
export function answersAgentRequests(
  profile: AcpProfile,
  name: string,
  options: AgentOptions,
  asking: AcpTurnEvents["asking"],
  signal: AbortSignal,
) {
  return async (method: string, params: unknown) => {
    if (method === "session/request_permission")
      return permission(
        options,
        name,
        params,
        signal,
        profile.provider === "antigravity",
      );
    if (method === "elicitation/create")
      return answerElicitation(options, name, params, signal, asking);
    throw new AcpRequestError(
      -32601,
      `Relay doesn't offer ${method}.`,
      null,
      method,
    );
  };
}
