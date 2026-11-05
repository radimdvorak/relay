// @vitest-environment happy-dom
import { afterEach, expect, it, vi } from "vitest";

/**
 * How many times each component rendered, by name, read off every commit the
 * way React's DevTools read it: a subtree that bailed out keeps its old
 * fibers, so the walk skips it, and a fiber that rendered has PerformedWork.
 */
const rendered = vi.hoisted(() => {
  const names = new Map<string, number>();
  type Fiber = {
    tag: number;
    flags: number;
    type: {
      name?: string;
      displayName?: string;
      type?: { name?: string };
      render?: { name?: string };
    } | null;
    child: Fiber | null;
    sibling: Fiber | null;
    alternate: Fiber | null;
  };
  const nameOf = (f: Fiber) =>
    f.type?.displayName ||
    f.type?.name ||
    f.type?.type?.name ||
    f.type?.render?.name ||
    "Anonymous";
  // Function, forwardRef and simple memo components.
  const component = (f: Fiber) => [0, 11, 15].includes(f.tag);
  const PERFORMED_WORK = 1;
  const walk = (parent: Fiber) => {
    for (let f = parent.child; f; f = f.sibling) {
      if (component(f) && (!f.alternate || f.flags & PERFORMED_WORK))
        names.set(nameOf(f), (names.get(nameOf(f)) ?? 0) + 1);
      if (!f.alternate || f.child !== f.alternate.child) walk(f);
    }
  };
  Object.assign(globalThis, {
    __REACT_DEVTOOLS_GLOBAL_HOOK__: {
      supportsFiber: true,
      isDisabled: false,
      renderers: new Map(),
      inject: () => 1,
      checkDCE: () => {},
      onScheduleFiberRoot: () => {},
      onCommitFiberUnmount: () => {},
      onPostCommitFiberRoot: () => {},
      onCommitFiberRoot: (_: number, root: { current: Fiber }) =>
        walk(root.current),
    },
  });
  return names;
});

/** Renders of each message row, by message id. */
const rows = vi.hoisted(() => new Map<string, number>());

const countedRow = vi.hoisted(
  () =>
    <P extends { message: { id: string } }>(inner: (props: P) => unknown) =>
    (props: P) => {
      rows.set(props.message.id, (rows.get(props.message.id) ?? 0) + 1);
      return inner(props);
    },
);

/** The desktop bridge, saying nothing: listeners never hear, calls find nothing. */
const bridge = vi.hoisted(
  () =>
    new Proxy(
      {},
      {
        get: (_, name: string) =>
          name.startsWith("on") ? () => () => {} : async () => undefined,
      },
    ),
);

/** Elements each ResizeObserver started watching, by their class. */
const watched = vi.hoisted(() => new Map<string, number>());

vi.hoisted(() => {
  const Observer = window.ResizeObserver;
  class CountingObserver extends Observer {
    observe(target: Element, options?: ResizeObserverOptions) {
      watched.set(target.className, (watched.get(target.className) ?? 0) + 1);
      super.observe(target, options);
    }
  }
  Object.assign(window, {
    ResizeObserver: CountingObserver,
    relay: bridge,
    requestIdleCallback: (run: () => void) => setTimeout(run, 0),
    cancelIdleCallback: (id: number) => clearTimeout(id),
  });
  Object.defineProperty(document, "fonts", {
    value: { ready: new Promise(() => {}) },
  });
});

vi.mock("../../lib/api", () => ({ api: bridge }));

// The desktop holds what the cache already does.
vi.mock("../../lib/chat-events", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../lib/chat-events")>()),
  fetchChat: async (qc: QueryClient, id: string) =>
    qc.getQueryData(["project-chat", id]),
}));

// Its speech model's state comes from the desktop, which says nothing here.
vi.mock("../dictation/DictationButton", () => ({
  DictationButton: () => null,
}));

vi.mock("./AssistantMessage", async (importOriginal) => {
  const original = await importOriginal<typeof import("./AssistantMessage")>();
  return {
    ...original,
    AssistantMessage: countedRow(original.AssistantMessage as never),
  };
});

vi.mock("./UserMessage", async (importOriginal) => {
  const original = await importOriginal<typeof import("./UserMessage")>();
  return {
    ...original,
    UserMessage: countedRow(original.UserMessage as never),
  };
});

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type {
  ChatMessage,
  ChatSummary,
  Project,
  ProjectChat as ProjectChatData,
} from "../../../shared/projects";
import { chatKey } from "../../lib/chat-events";
import { ProjectChat } from "./ProjectChat";

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

const project = {
  id: "p1",
  name: "Sample",
  path: "/sample/relay",
} as unknown as Project;

const summary = {
  id: "c1",
  projectId: "p1",
  title: "Thread",
  scope: { kind: "project" },
  created: 1,
  updated: 1,
} as unknown as ChatSummary;

const message = (
  id: string,
  role: ChatMessage["role"],
  body: string,
  extra: Partial<ChatMessage> = {},
): ChatMessage => ({
  id,
  role,
  body,
  created: Number(id.slice(1)),
  provider: "claude",
  status: "complete",
  version: 1,
  ...extra,
});

/** Five finished turns, then a prompt whose answer is streaming. */
const finished = [1, 2, 3, 4, 5]
  .flatMap((n) => [
    message(`u${n * 2}`, "user", `Question ${n}`),
    message(`a${n * 2 + 1}`, "assistant", `Answer ${n}, with **markdown**.`),
  ])
  .concat(message("u20", "user", "Last question"));

// Past the opening the timeline previews, as most of an answer is.
const opening = "Looking into it. ".repeat(30);

let root: Root | undefined;
let container: HTMLElement | undefined;

afterEach(() => {
  act(() => root?.unmount());
  container?.remove();
  root = container = undefined;
  rendered.clear();
  rows.clear();
  watched.clear();
});

const noop = () => {};
const later = async () => {};

/** The open thread, its last answer streaming; returns a streamed piece. */
async function openStreamingThread() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  let answer = message("a21", "assistant", opening, { status: "streaming" });
  qc.setQueryData<ProjectChatData>(chatKey("c1"), {
    ...summary,
    messages: [...finished, answer],
  } as ProjectChatData);
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
  await act(async () => {
    root!.render(
      <QueryClientProvider client={qc}>
        <ProjectChat
          onCommand={() => false}
          project={project}
          projects={[project]}
          chat={summary}
          draftId="draft"
          draftScope={{ kind: "project" }}
          onContextUsed={noop}
          onCreated={later}
          onForked={later}
          onOpenThread={later}
          scopes={{
            canChoosePR: false,
            onClearScope: noop,
            onSelectPR: noop,
            onDeepReview: noop,
          }}
          onSwitchProject={noop}
          onAddProject={noop}
          onProjectSettings={noop}
          opens={{ onOpenCode: noop, onOpenFile: noop, onOpenTurnDiff: noop }}
          viewing={{ path: null, viewed: 0, total: 0 }}
        />
      </QueryClientProvider>,
    );
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  // As lib/chat-events puts a pushed version of the answer in the cache.
  return async () => {
    answer = {
      ...answer,
      body: `${answer.body} more`,
      version: answer.version + 1,
    };
    await act(async () => {
      qc.setQueryData<ProjectChatData>(chatKey("c1"), (chat) => ({
        ...chat!,
        messages: chat!.messages.map((m) => (m.id === answer.id ? answer : m)),
      }));
      // The cache tells its observers on a timeout.
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  };
}

/** Three streamed pieces into the open thread's answer, counted from scratch. */
async function streamThreePieces() {
  const streamed = await openStreamingThread();
  rendered.clear();
  rows.clear();
  watched.clear();
  for (let piece = 0; piece < 3; piece++) await streamed();
}

it("re-renders only the streaming answer's row, not the finished ones", async () => {
  await streamThreePieces();
  expect(Object.fromEntries(rows)).toEqual({ a21: 3 });
});

it("leaves the notes line under every message alone", async () => {
  await streamThreePieces();
  expect(rendered.get("MessageNotes") ?? 0).toBe(0);
});

it("doesn't measure the timeline again while its turns read the same", async () => {
  await streamThreePieces();
  expect(watched.get("project-messages") ?? 0).toBe(0);
  expect(watched.get("thread-message-column") ?? 0).toBe(0);
});

// The composer and the controls around it still render on every piece:
// ProjectChat reads the whole thread and hands them fresh objects. This may
// shrink, never grow; lower it when you cut renders.
it("renders at most 93 components a streamed piece", async () => {
  await streamThreePieces();
  const total = [...rendered.values()].reduce((sum, n) => sum + n, 0);
  expect(total / 3).toBeLessThanOrEqual(93);
});
