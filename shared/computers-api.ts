// What the window asks about the paired computers: pairing them, handing
// threads to them and bringing them back, and projects that live on them.
import type {
  ComputerRoute,
  ComputersOverview,
  HandoffTarget,
  HandoffView,
  PairedComputer,
  RouteCheck,
  SshHost,
} from "./handoff";
import type { ComputerThreads } from "./computer-threads";
import type { ComputerFolders } from "./remote";
import type { Project, ProjectChatSend } from "./projects";
import type { UpdateState } from "./updates";

export interface ComputersApi {
  pairedComputers(): Promise<PairedComputer[]>;
  computersOverview(): Promise<ComputersOverview>;
  /**
   * Pairs with the link another computer shows under Settings → Computers.
   * The list ends with the computer just paired, also when it was paired before.
   */
  pairComputer(link: string): Promise<PairedComputer[]>;
  /** Reaches the computer a route goes to and says which Relay is there. */
  checkComputer(route: ComputerRoute): Promise<RouteCheck>;
  /** Pairs with the Relay a route reaches, over SSH or into WSL. */
  connectComputer(route: ComputerRoute): Promise<PairedComputer[]>;
  /** Ends the check or pairing under way over that route. */
  stopComputerCheck(route: ComputerRoute): Promise<void>;
  /** The WSL distributions on this computer; none off Windows. */
  wslDistros(): Promise<string[]>;
  /** Installs Relay where the route goes, over it; resolves once it's in. */
  installRelayOver(route: ComputerRoute): Promise<void>;
  /** What that install has printed so far, the end of it. */
  relayInstallOutput(route: ComputerRoute): Promise<string>;
  /** The hosts this computer's ~/.ssh/config names, to suggest for Over SSH. */
  sshHosts(): Promise<SshHost[]>;
  /** Starts a WSL distribution WSL stopped again, and connects to it. */
  wakeComputer(id: string): Promise<PairedComputer[]>;
  forgetComputer(id: string): Promise<PairedComputer[]>;
  handoffTargets(chatId: string): Promise<HandoffTarget[]>;
  /** Each paired computer's own threads, as it lists them now. */
  computerThreads(): Promise<ComputerThreads[]>;
  /** The folders in `dir` on a paired computer, `~` its home folder. */
  computerFolders(computerId: string, dir: string): Promise<ComputerFolders>;
  /** Adds a folder on a paired computer as a project that lives there. */
  addRemoteProject(computerId: string, path: string): Promise<Project>;
  /** Starts or polls a clone on a paired computer, then adds its project here. */
  cloneRemoteProject(
    computerId: string,
    url: string,
    into: string,
  ): Promise<
    | { done: false; step: string; progress: number | null }
    | { done: true; project: Project }
  >;
  /** The paired computers a new thread in the project can start on, or why each can't. */
  startTargets(projectId: string): Promise<HandoffTarget[]>;
  /**
   * Starts a new thread on another computer: its first message is kept
   * here unanswered and the thread goes there, where it's answered.
   */
  startProjectChatOn(
    chatId: string,
    input: ProjectChatSend,
    computerId: string,
  ): Promise<void>;
  /**
   * For a thread whose project an agent is setting up on the computer it's
   * going to: the folder there that holds a clone, added there as its
   * project when it does. False when it doesn't hold the repository.
   */
  setupFolder(chatId: string, folder: string): Promise<boolean>;
  /** Gives up on the setup a thread waits for there; it stays, and starts, here. */
  startThreadHere(chatId: string): Promise<void>;
  /** Starts the handoff; the thread's `sentTo` follows it from there. */
  handOffThread(chatId: string, computerId: string): Promise<void>;
  /** Sends a handoff that failed on its way again. */
  retryHandoff(chatId: string): Promise<void>;
  handoffView(chatId: string): Promise<HandoffView | null>;
  /** Every thread that's on another computer, or on its way, by chat id. */
  handoffViews(): Promise<Record<string, HandoffView>>;
  /** `park` brings it back even if its work clashes, left at its handoff ref. */
  bringBackThread(chatId: string, park?: boolean): Promise<void>;
  /** Keeps a thread whose handoff failed here; refused unless the other side says it never got it. */
  keepThreadHere(chatId: string): Promise<void>;
  /**
   * Unlocks a thread that's away, as it was when it left, without the other
   * computer. What was done there stays there; it is told when it can be.
   */
  abandonHandoff(chatId: string): Promise<void>;
  /**
   * Carries on a live thread here while its computer is offline: the
   * conversation stays, the code is as it left, and what waited goes out
   * here. That computer is told when it can be. What fails to go out, and
   * all after it, waits again; called once the thread is here, it sends those.
   */
  runThreadHere(chatId: string): Promise<void>;
  /** Drops a send that waits for the computer a live thread is on. */
  dropHeldSend(chatId: string, sendId: string): Promise<void>;
  /** Has a paired computer update Relay and restart; it reconnects by itself. */
  updateComputer(id: string): Promise<UpdateState>;
}
