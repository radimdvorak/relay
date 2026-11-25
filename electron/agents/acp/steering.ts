// Steers a running turn with another `session/prompt`, for an agent that takes
// one into the turn it's on (Antigravity 1.5). The earlier prompt then answers
// at once, and the latest one's answer is the turn's.
import type { AgentOptions } from "../types";
import { guardSteer } from "../turn-kit";
import { promptResultSchema, type AcpPromptResult } from "./protocol";

type Steer = Parameters<NonNullable<AgentOptions["onControl"]>>[0]["steer"];

/** A prompt on its way to the agent: the request's id, and its answer. */
export type SentPrompt = { request: number; reply: Promise<unknown> };

/** One prompt of the turn; `id` names the message a steer came from. */
type Prompt = SentPrompt & { answered: boolean; id?: string };

export class PromptSteering {
  /** The turn's prompts the agent hasn't refused, in the order they were sent. */
  private prompts: Prompt[];
  private ended = false;

  constructor(
    first: SentPrompt,
    private readonly io: {
      /** Sends a prompt on the turn's session, which a restarted Relay then waits on. */
      send(prompt: object[]): SentPrompt;
      /** Has a restarted Relay wait on this request instead of the last one sent. */
      waitOn(request: number): void;
      images(images: AgentOptions["images"]): Promise<object[]>;
      /** The turn is over, or stopping. */
      over(): boolean;
      /** The agent waits for an answer to a question or approval, and takes no prompt meanwhile. */
      waiting(): boolean;
      /** The agent took the steer up: what follows answers it. */
      steered(id: string): void;
      unread(id: string, error: Error): void;
    },
  ) {
    this.prompts = [{ ...first, answered: false }];
    this.follow(this.prompts[0]);
  }

  readonly steer: Steer = (text, id, images) =>
    guardSteer(
      () => !this.ended && !this.io.over() && !this.prompts.at(-1)!.answered,
      async () => [{ type: "text", text }, ...(await this.io.images(images))],
      (prompt) => {
        if (this.io.waiting())
          throw new Error(
            "It's waiting for your answer. Answer it, and the message goes after this answer.",
          );
        this.add(prompt, id);
      },
    );

  private add(prompt: object[], id: string | undefined) {
    const steer: Prompt = { ...this.io.send(prompt), answered: false, id };
    this.prompts.push(steer);
    this.follow(steer);
  }

  private follow(prompt: Prompt) {
    prompt.reply.then(
      () => {
        prompt.answered = true;
        // The agent let this one go for the next: that's it taking the steer up.
        const next = this.prompts[this.prompts.indexOf(prompt) + 1];
        if (next?.id) this.io.steered(next.id);
      },
      (error: Error) => {
        const at = this.prompts.indexOf(prompt);
        const takenUp = at < 1 || this.prompts[at - 1].answered;
        if (takenUp) return;
        // Refused: the turn goes on as it was, without the message.
        this.prompts.splice(at, 1);
        if (!this.ended) this.io.waitOn(this.prompts.at(-1)!.request);
        if (prompt.id && !this.io.over()) this.io.unread(prompt.id, error);
      },
    );
  }

  private get latest() {
    return this.prompts[this.prompts.length - 1].reply;
  }

  /**
   * Waits for the turn's result: the latest prompt's, unless one of `endings`
   * ends the turn first. `interim` hears what earlier prompts answered.
   */
  async result(
    endings: (Promise<never> | undefined)[],
    interim: (result: AcpPromptResult) => void,
  ) {
    try {
      for (let at = 0; ; at++) {
        const { reply } = this.prompts[at];
        const result = promptResultSchema.parse(
          await Promise.race([
            reply,
            ...endings.filter((e) => e !== undefined),
          ]),
        );
        if (reply === this.latest) return result;
        interim(result);
      }
    } finally {
      this.ended = true;
    }
  }
}
