import { streamedIfReal, type Streamed } from "../../../shared/usage";
import type { AcpToolCall } from "../acp/protocol";
import { startsSubagent } from "./subagents";

/**
 * How fast Antigravity wrote a prompt's text. The CLI counts a whole prompt's
 * output at once, the arguments of its tool calls included, and those arrive
 * only once they're written. So the clock runs while text streams, and the
 * text gets its share of the output by length. Thinking is counted apart.
 */
export class TextSpeed {
  private textLength = 0;
  /** Text that came while the clock ran: all but the first piece of a stretch. */
  private timedLength = 0;
  private ms = 0;
  private lastPiece?: number;
  private calls = new Map<string, number>();
  /** Whether what a subagent wrote counts as this prompt's output isn't known. */
  private subagents = false;

  constructor(private now = Date.now) {}

  text(text: string) {
    const at = this.now();
    if (this.lastPiece !== undefined) {
      this.ms += at - this.lastPiece;
      this.timedLength += text.length;
    }
    this.lastPiece = at;
    this.textLength += text.length;
  }

  /** The model thinks, or calls a tool: the next text starts a stretch of its own. */
  pause() {
    this.lastPiece = undefined;
  }

  call(call: Partial<AcpToolCall> & { toolCallId: string }) {
    this.pause();
    if (startsSubagent(call)) this.subagents = true;
    this.calls.set(call.toolCallId, JSON.stringify(call.rawInput ?? {}).length);
  }

  /** The prompt ended, having shown `output` tokens; the next one starts afresh. */
  finish(output: number): Streamed | undefined {
    const { textLength, timedLength, ms, subagents } = this;
    const argsLength = [...this.calls.values()].reduce((a, b) => a + b, 0);
    this.textLength = this.timedLength = this.ms = 0;
    this.calls.clear();
    this.subagents = false;
    this.pause();
    if (!timedLength || subagents) return;
    const share = timedLength / (textLength + argsLength);
    return streamedIfReal(Math.round(output * share), ms);
  }
}
