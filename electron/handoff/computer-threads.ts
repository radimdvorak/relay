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
import type { ProjectChats } from "../project-chats";
import type { Projects } from "../projects/projects";
import type { ComputerInfos } from "./computer-infos";
import type { Computers } from "./computers";
import type { HandoffFollowing } from "./following";

export class ComputerThreads {
  /** Threads being opened, so a second click waits for the first. */
  private opening = new Map<string, Promise<ComputerThreadHere>>();

  constructor(
    private store: Store,
    private computers: Computers,
    private infos: ComputerInfos,
    private projects: Projects,
    private chats: ProjectChats,
    private following: HandoffFollowing,
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

  /**
   * The thread here that follows one on the computer, made the first time:
   * in its project there, added here as one that lives there, and kept as
   * a mirror that runs there, as a thread started in that project from here.
   */
  open(computerId: string, threadId: string) {
    const key = `${computerId}:${threadId}`;
    let job = this.opening.get(key);
    if (!job) {
      job = this.follow(computerId, threadId).finally(() =>
        this.opening.delete(key),
      );
      this.opening.set(key, job);
    }
    return job;
  }

  private async follow(
    computerId: string,
    threadId: string,
  ): Promise<ComputerThreadHere> {
    const known = this.followedFrom(computerId).get(threadId);
    if (known) return known;
    const { name } = this.computers.get(computerId);
    const client = await this.computers.connected(computerId);
    const { projects, threads } = await client.call("computerThreads");
    const thread = threads.find((t) => t.id === threadId);
    const there = projects.find((p) => p.id === thread?.projectId);
    if (!thread || !there)
      throw new Error(`That thread isn't on ${name} anymore.`);
    const project = await this.projects.addRemote(
      { id: computerId, name },
      {
        id: there.id,
        name: there.name,
        path: there.folder,
        plain: there.plain,
      },
    );
    const chat = await this.chats.liveHandoff.followThere(project.id, thread, {
      id: thread.id,
      computerId,
      computer: name,
      at: Date.now(),
      state: "away",
      live: true,
      kept: 0,
      folder: true,
      home: true,
    });
    await this.following.follow(computerId, chat.id);
    return { projectId: project.id, chatId: chat.id };
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
