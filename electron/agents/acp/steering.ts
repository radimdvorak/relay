// Steers a running turn with another `session/prompt`, for an agent that takes
// one into the turn it's on (Antigravity 1.5). The earlier prompt then answers
// at once, and the latest one's answer is the turn's.
import { z } from "zod";
import type { AgentOptions } from "../types";
import type { Settled } from "../line-connection";
import { guardSteer } from "../turn-kit";
import { promptResultSchema, type AcpPromptResult } from "./protocol";

type Steer = Parameters<NonNullable<AgentOptions["onControl"]>>[0]["steer"];

/** Where a turn's prompts stand, for the next Relay to pick up. */
export const keptRunSchema = z.object({
  /** The latest prompt's request. */
  id: z.number(),
  /** An earlier Relay's compaction, which took two prompts. */
  compaction: z.boolean().optional(),
  /** The prompts not answered yet, in order; `steer` names the message one came from. */
  prompts: z
    .array(z.object({ request: z.number(), steer: z.string().optional() }))
    .optional(),
  /** The last steer taken up, and the request whose answer it follows. */
  takenUp: z.object({ steer: z.string(), after: z.number() }).optional(),
});

export type KeptRun = z.infer<typeof keptRunSchema>;

/** One prompt of the turn; `steer` names the message it came from. */
type Prompt = {
  request: number;
  steer?: string;
  reply: Promise<unknown>;
  answered: boolean;
};

type Heard = (settled: Settled) => void;

export class PromptSteering {
  /** The turn's prompts the agent hasn't refused, in the order they were sent. */
  private prompts: Prompt[] = [];
  private takenUp: KeptRun["takenUp"];
  private ended = false;

  constructor(
    private readonly io: {
      /** The id the next prompt's request gets. */
      reserve(): number;
      /** Sends a prompt on the turn's session; `heard` gets its reply before anything said after it. */
      send(request: number, prompt: object[], heard: Heard): Promise<unknown>;
      /** Waits on a prompt an earlier Relay sent. */
      wait(request: number, heard: Heard): Promise<unknown>;
      /** Keeps where the turn stands for a restarted Relay. */
      keep(run: KeptRun): void;
      images(images: AgentOptions["images"]): Promise<object[]>;
      /** The turn is over, or stopping. */
      over(): boolean;
      /** The agent waits for an answer to a question or approval, and takes no prompt meanwhile. */
      waiting(): boolean;
      /** The agent took the steer up: what follows answers it. */
      steered(id: string): void;
      unread(id: string, error: Error): void;
      /** A prompt that keeps a turn going the agent ended too early, if it did. */
      carryOn(result: AcpPromptResult): string | undefined;
    },
  ) {}

  /** Sends the turn's first prompt. */
  start(prompt: object[]) {
    this.add(prompt);
  }

  /** Waits on the prompts an earlier Relay sent, from where it left off. */
  pickUp(run: KeptRun) {
    this.takenUp = run.takenUp;
    // The answer below the steer may not have been saved yet.
    if (run.takenUp) this.io.steered(run.takenUp.steer);
    const sent = run.prompts ?? [{ request: run.id }];
    const prompts = sent.map(({ request, steer }) => this.push(request, steer));
    for (const prompt of prompts)
      this.follow(prompt, (heard) => this.io.wait(prompt.request, heard));
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

  private add(prompt: object[], steer?: string) {
    const next = this.push(this.io.reserve(), steer);
    this.keep();
    this.follow(next, (heard) => this.io.send(next.request, prompt, heard));
  }

  private push(request: number, steer: string | undefined) {
    const prompt: Prompt = {
      request,
      steer,
      reply: Promise.resolve(),
      answered: false,
    };
    this.prompts.push(prompt);
    return prompt;
  }

  private follow(prompt: Prompt, ask: (heard: Heard) => Promise<unknown>) {
    prompt.reply = ask(({ error }) =>
      error ? this.failed(prompt, error) : this.answered(prompt),
    );
    // Whoever waits for the turn hears of a failure; a refused steer has no one.
    void prompt.reply.catch(() => {});
  }

  private answered(prompt: Prompt) {
    prompt.answered = true;
    const next = this.prompts[this.prompts.indexOf(prompt) + 1];
    if (!next) return;
    // The agent let this one go for the next: that's it taking the steer up.
    if (next.steer) this.takenUp = { steer: next.steer, after: prompt.request };
    if (!this.ended) this.keep();
    if (next.steer) this.io.steered(next.steer);
  }

  private failed(prompt: Prompt, error: Error) {
    const at = this.prompts.indexOf(prompt);
    const takenUp = at < 1 || this.prompts[at - 1].answered;
    if (takenUp) return;
    // Refused: the turn goes on as it was, without the message.
    this.prompts.splice(at, 1);
    if (!this.ended) this.keep();
    if (prompt.steer && !this.io.over()) this.io.unread(prompt.steer, error);
  }

  private keep() {
    const unanswered = this.prompts.filter((prompt) => !prompt.answered);
    this.io.keep({
      id: this.prompts.at(-1)!.request,
      prompts: unanswered.map(({ request, steer }) => ({ request, steer })),
      takenUp: this.takenUp,
    });
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
        if (reply === this.latest) {
          const text = this.io.over() ? undefined : this.io.carryOn(result);
          if (!text) return result;
          this.add([{ type: "text", text }]);
        }
        interim(result);
      }
    } finally {
      this.ended = true;
    }
  }
}
