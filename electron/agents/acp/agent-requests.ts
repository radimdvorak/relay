// What an ACP agent asks Relay while a turn runs: to allow a tool, or to fill
// in a form. Nothing else is offered to it.
import type { AgentOptions } from "../types";
import { AcpRequestError } from "./connection";
import { answerElicitation } from "./elicitation";
import { permission } from "./permission";
import type { AcpProfile } from "./profiles";
import type { AcpTurnEvents } from "./turn-events";

/** Answers the running turn's requests, and knows whether one is still unanswered. */
export class AgentRequests {
  private open = 0;

  constructor(
    private readonly profile: AcpProfile,
    private readonly name: string,
    private readonly options: AgentOptions,
    private readonly asking: AcpTurnEvents["asking"],
    /** Ends with the turn. */
    private readonly signal: AbortSignal,
  ) {}

  /** The agent waits for an answer, the user's or Relay's. */
  get waiting() {
    return this.open > 0;
  }

  readonly answer = async (method: string, params: unknown) => {
    this.open++;
    try {
      return await this.respond(method, params);
    } finally {
      this.open--;
    }
  };

  private respond(method: string, params: unknown) {
    if (method === "session/request_permission")
      return permission(
        this.options,
        this.name,
        params,
        this.signal,
        this.profile.provider === "antigravity",
      );
    if (method === "elicitation/create")
      return answerElicitation(
        this.options,
        this.name,
        params,
        this.signal,
        this.asking,
      );
    throw new AcpRequestError(
      -32601,
      `Relay doesn't offer ${method}.`,
      null,
      method,
    );
  }
}
