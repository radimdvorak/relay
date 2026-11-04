// The paired computers' own threads: what each lists, so the Computers
// sidebar can show them beside the ones that went there from here.
import { liveAway, type PairedComputer } from "../../shared/handoff";
import type {
  ComputerThreadHere,
  ComputerThreads as ListedThreads,
} from "../../shared/computer-threads";
import { errorMessage } from "../../shared/error-message";
import { computerThreadsBridge } from "../../shared/remote";
import type { Store } from "../app/store";
import type { ComputerInfos } from "./computer-infos";
import type { Computers } from "./computers";

export class ComputerThreads {
  constructor(
    private store: Store,
    private computers: Computers,
    private infos: ComputerInfos,
  ) {}

  /** Every paired computer's threads, as each lists them now. */
  list(): Promise<ListedThreads[]> {
    return Promise.all(this.computers.list().map((c) => this.listOn(c)));
  }

  private async listOn(computer: PairedComputer): Promise<ListedThreads> {
    const { id, name } = computer;
    // A WSL distribution asleep isn't woken just to list it.
    const online = computer.status === "online" && !computer.asleep;
    const none = { id, name, online, projects: [], threads: [] };
    if (!online) return none;
    try {
      const client = await this.computers.connected(id);
      const info = await this.infos.get(id, client);
      if (!info || info.bridge < computerThreadsBridge)
        return {
          ...none,
          problem: `Update Relay on ${name} to see its threads here.`,
        };
      const { projects, threads } = await client.call("computerThreads");
      const here = this.followedFrom(id);
      return {
        ...none,
        projects,
        threads: threads.map((t) => {
          const chat = here.get(t.id);
          return chat ? { ...t, here: chat } : t;
        }),
      };
    } catch (e) {
      return { ...none, problem: errorMessage(e) };
    }
  }

  /** The threads here that follow one on the computer, by what they follow it by. */
  private followedFrom(computerId: string) {
    const here = new Map<string, ComputerThreadHere>();
    for (const c of this.store.get().chats ?? [])
      if (c.sentTo?.computerId === computerId && liveAway(c))
        here.set(c.sentTo.id, { projectId: c.projectId, chatId: c.id });
    return here;
  }
}
