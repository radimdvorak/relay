// PowerShell's cmdlets read as the reads and searches they are. Codex on
// Windows runs its commands in PowerShell, so where it would run
// `sed -n 151,220p a.ts` it runs `Get-Content a.ts | Select-Object -Skip 150 -First 70`.
import type { CallAction } from "./call-actions";
import { pipeAt, pipedInto } from "./shell-words";

/** Cmdlets that only set up for the commands after them, in lowercase: PowerShell ignores case. */
const setup = new Set([
  "set-location", "sl", "get-location", "gl", "pwd", "push-location", "pop-location", "test-path", "write-output",
  "write-host",
]);

/** What a PowerShell command did; undefined when it isn't a cmdlet known here. */
export function cmdletActions(segment: string): CallAction[] | undefined {
  // `$env:UE_TIMEOUT = '120000'` only sets a value for what comes next.
  if (/^\s*\$[\w:]+\s*=/.test(segment)) return [];
  const at = pipeAt(segment);
  const [name = [], ...args] = cmdletArgs(at === -1 ? segment : segment.slice(0, at));
  const cmdlet = name[0]?.toLowerCase() ?? "";
  if (setup.has(cmdlet)) return [];
  switch (cmdlet) {
    case "get-content":
    case "gc":
    case "type":
      return contentReads(parameters(args), segment);
    case "select-string":
    case "sls":
      return [stringSearch(parameters(args))];
    default:
      return undefined;
  }
}

type Parameters = { named: Map<string, string[]>; positional: string[][] };

/** `Get-Content a.ts -TotalCount 50` reads lines 1–50, and so does piping it into `Select-Object -First 50`. */
function contentReads({ named, positional }: Parameters, segment: string): CallAction[] {
  const files = named.get("path") ?? named.get("literalpath") ?? named.get("lp") ?? positional[0] ?? [];
  const count = named.get("totalcount") ?? named.get("head") ?? named.get("first");
  const lines = count ? linesKept(undefined, count[0]) : selectedLines(segment);
  // Lines kept from what several files printed together aren't any one file's.
  return files.map((file) => (lines && files.length === 1 ? { kind: "read", file, lines } : { kind: "read", file }));
}

/** The lines a `| Select-Object -Skip 150 -First 70` kept: "151–220". */
function selectedLines(segment: string) {
  const [name = [], ...args] = cmdletArgs(pipedInto(segment) ?? "");
  if (!/^select(?:-object)?$/i.test(name[0] ?? "")) return undefined;
  const { named } = parameters(args);
  return linesKept(named.get("skip")?.[0], named.get("first")?.[0]);
}

function linesKept(skip: string | undefined, first: string | undefined) {
  const skipped = Number(skip ?? 0);
  const kept = Number(first);
  if (!Number.isInteger(skipped) || !Number.isInteger(kept) || kept < 1) return undefined;
  return `${skipped + 1}–${skipped + kept}`;
}

/** `Select-String -Path a.ts -Pattern foo`, or `Select-String foo a.ts`. */
function stringSearch({ named, positional }: Parameters): CallAction {
  const patterns = named.get("pattern") ?? positional[0] ?? [];
  const where = named.get("path") ?? named.get("literalpath") ?? positional[named.has("pattern") ? 0 : 1] ?? [];
  return { kind: "search", pattern: patterns.join(" | "), ...(where.length ? { where: where.join(" ") } : {}) };
}

/** Parameters that take no value, so the word after one is an argument of its own. */
const switches = new Set([
  "raw", "recurse", "force", "simplematch", "casesensitive", "list", "quiet", "notmatch", "allmatches", "wait",
  "asbytestream", "unique",
]);

/** Arguments by the parameter they're given to, in lowercase, and the rest in order. */
function parameters(args: string[][]): Parameters {
  const named = new Map<string, string[]>();
  const positional: string[][] = [];
  for (let i = 0; i < args.length; i++) {
    const flag = /^-([a-z]\w*)(?::(.+))?$/i.exec(args[i]!.length === 1 ? args[i]![0]! : "");
    if (!flag) {
      positional.push(args[i]!);
      continue;
    }
    const name = flag[1]!.toLowerCase();
    if (flag[2] !== undefined) named.set(name, [flag[2]]);
    else if (switches.has(name)) named.set(name, []);
    else named.set(name, args[++i] ?? []);
  }
  return { named, positional };
}

/**
 * A command's arguments, quotes off, each a list: `'a.ts', 'b.ts'` is one
 * argument of two values. `''` inside single quotes is a quote, and a
 * backtick takes the next character as text.
 */
function cmdletArgs(command: string) {
  const args: string[][] = [];
  let values: string[] = [];
  let value = "";
  let inValue = false;
  // After a comma, the next value belongs to the same argument.
  let listGoesOn = false;
  let quote = "";
  const endValue = () => {
    if (inValue) values.push(value);
    value = "";
    inValue = false;
  };
  const endArg = () => {
    endValue();
    if (values.length) args.push(values);
    values = [];
  };
  for (let i = 0; i < command.length; i++) {
    const c = command[i]!;
    if (quote) {
      if (c === quote && command[i + 1] === quote) value += command[++i];
      else if (c === quote) quote = "";
      else if (quote === '"' && c === "`") value += command[++i] ?? "";
      else value += c;
    } else if (c === ",") {
      endValue();
      listGoesOn = true;
    } else if (/\s/.test(c)) {
      if (!listGoesOn) endArg();
    } else {
      inValue = true;
      listGoesOn = false;
      if (c === "'" || c === '"') quote = c;
      else if (c === "`") value += command[++i] ?? "";
      else value += c;
    }
  }
  endArg();
  return args;
}
