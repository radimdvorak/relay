// What the OS says about processes: where each one works and the ports it
// listens on.
import { execFile } from "node:child_process";
import { readlink } from "node:fs/promises";

const windows = process.platform === "win32";

/** A system tool's output, or nothing when it fails or takes over 5 seconds. */
export function run(file: string, args: string[]) {
  return new Promise<string>((resolve) => {
    execFile(
      file,
      args,
      {
        maxBuffer: 16 << 20,
        timeout: 5000,
        windowsHide: true,
        env: { ...process.env, LC_ALL: "C" },
      },
      (_e, out) => resolve(out ?? ""),
    );
  });
}

/** Linux marks the working folder of a process whose folder was deleted with a suffix. */
export const liveCwd = (link: string) => link.replace(/ \(deleted\)$/, "");

/**
 * The folder each of `pids` works in, or null where the OS doesn't say. One
 * `lsof` call for all of them. Not for Windows, which keeps a process's
 * folder where only a debugger reads it.
 */
export async function workingFolders(pids: number[]) {
  const folders = new Map<number, string | null>();
  if (process.platform === "linux") {
    await Promise.all(
      pids.map(async (pid) =>
        folders.set(
          pid,
          await readlink(`/proc/${pid}/cwd`).then(liveCwd, () => null),
        ),
      ),
    );
    return folders;
  }
  const out = await run("lsof", [
    "-a",
    "-d",
    "cwd",
    "-Fn",
    "-p",
    pids.join(","),
  ]);
  const found = lsofFolders(out);
  for (const pid of pids) folders.set(pid, found.get(pid) ?? null);
  return folders;
}

/** `lsof -d cwd -Fn`'s answer, by pid. */
function lsofFolders(out: string) {
  const found = new Map<number, string>();
  let pid = 0;
  for (const line of out.split("\n"))
    if (line[0] === "p") pid = +line.slice(1);
    else if (line[0] === "n" && pid) found.set(pid, line.slice(1));
  return found;
}

let portLook:
  { wanted: Set<number>; done: Promise<Map<number, number[]>> } | undefined;

/**
 * The ports each of `pids` listens on. Lists asked for together, as Sites
 * does over every project, share one look: the pids asked for before the
 * next turn of the event loop.
 */
export function listeningPorts(pids: number[]): Promise<Map<number, number[]>> {
  if (!pids.length) return Promise.resolve(new Map());
  if (!portLook) {
    const wanted = new Set<number>();
    portLook = {
      wanted,
      done: new Promise((resolve) => setImmediate(resolve)).then(() => {
        portLook = undefined;
        return listening([...wanted]);
      }),
    };
  }
  for (const pid of pids) portLook.wanted.add(pid);
  return portLook.done;
}

async function listening(pids: number[]) {
  const ports = new Map<number, number[]>();
  if (windows) {
    const wanted = new Set(pids);
    for (const [pid, port] of netstatListening(await run("netstat", ["-ano"])))
      if (wanted.has(pid)) ports.set(pid, [...(ports.get(pid) ?? []), port]);
    for (const [pid, list] of ports) ports.set(pid, [...new Set(list)]);
    return ports;
  }
  const out = await run("lsof", [
    "-nP",
    "-a",
    "-iTCP",
    "-sTCP:LISTEN",
    "-Fn",
    "-p",
    pids.join(","),
  ]);
  let pid = 0;
  for (const line of out.split("\n"))
    if (line[0] === "p") pid = +line.slice(1);
    else if (line[0] === "n" && pid) {
      const port = Number(/:(\d+)$/.exec(line)?.[1]);
      if (port) ports.set(pid, [...(ports.get(pid) ?? []), port]);
    }
  return ports;
}

/**
 * `netstat -ano` rows listening for connections, as [pid, port]. The state's
 * name is translated on non-English Windows; a listening socket is the one
 * whose remote end is port 0.
 */
export function netstatListening(out: string): [number, number][] {
  return out.split(/\r?\n/).flatMap((row) => {
    const m = /^\s*TCP\s+\S*:(\d+)\s+\S*:0\s+\S+\s+(\d+)\s*$/i.exec(row);
    return m ? [[+m[2], +m[1]] as [number, number]] : [];
  });
}
