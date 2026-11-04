// A shell command line read the way the shell splits it, enough to tell what
// each part runs: quotes, `;` and `&&` chains, pipes, heredocs, and the
// `/bin/zsh -lc '…'` Codex wraps every command in.

/**
 * Codex runs everything as `/bin/zsh -lc '<command>'`, and on Windows as
 * `"C:\\…\\powershell.exe" -Command "<command>"`; a label cut short may have
 * lost the closing quote.
 */
export function unwrapShell(command: string): string {
  const windows = windowsShell.exec(command);
  if (windows) return shellArgs(windows[1]!).join(" ");
  const wrapped = /^\s*(?:\S*\/)?(?:ba|z|fi)?sh\s+-\w*c\s+(['"])([\s\S]*)$/.exec(command);
  if (!wrapped) return command;
  const quote = wrapped[1]!;
  const rest = wrapped[2]!.trimEnd();
  const inner = rest.endsWith(quote) ? rest.slice(0, -1) : rest;
  // Inside single quotes a quote is written '\''; inside double quotes, \" \\ \$ \`.
  return quote === "'" ? inner.replaceAll(`'\\''`, "'") : inner.replace(/\\(["\\$`])/g, "$1");
}

/** Whether a Windows shell ran the command, where `type a.txt` prints a file. */
export const ranInWindowsShell = (command: string) => windowsShell.test(command);

/**
 * `powershell.exe -NoProfile -Command …`, `pwsh -c …` or `cmd.exe /c …`, the
 * program's path maybe quoted; what follows is the command, shell-quoted.
 */
const windowsShell = new RegExp(
  String.raw`^\s*(?:"[^"\n]*[\\/]|'[^'\n]*[\\/]|[^\s"']*[\\/])?` +
    String.raw`(?:(?:powershell|pwsh)(?:\.exe)?["']?\s+(?:-(?!c(?:ommand)?\s)\w+\s+(?:[^-\s]\S*\s+)?)*-c(?:ommand)?` +
    String.raw`|cmd(?:\.exe)?["']?\s+(?:\/\w\s+)*\/c)\s+([\s\S]*)$`,
  "i",
);

/**
 * The words a shell-quoted line hands its program: quotes off and the pieces
 * of `'"'!x'"'` joined back into `'!x'`. A quote a label cut short runs to the end.
 */
function shellArgs(line: string) {
  const args: string[] = [];
  let arg = "";
  let inArg = false;
  let quote = "";
  for (let i = 0; i < line.length; i++) {
    const c = line[i]!;
    if (quote) {
      if (c === quote) quote = "";
      // Inside double quotes a backslash only takes " \ $ ` as text.
      else if (quote === '"' && c === "\\" && /["\\$`]/.test(line[i + 1] ?? "")) arg += line[++i];
      else arg += c;
    } else if (/\s/.test(c)) {
      if (inArg) args.push(arg);
      arg = "";
      inArg = false;
    } else {
      inArg = true;
      if (c === "'" || c === '"') quote = c;
      else if (c === "\\") arg += line[++i] ?? "";
      else arg += c;
    }
  }
  if (inArg) args.push(arg);
  return args;
}

/** Where a line's quoted stretches start and end, so a `<<` or `|` inside them is text. */
function quotedSpans(line: string) {
  const spans: [number, number][] = [];
  let quote = "";
  let start = 0;
  for (let i = 0; i < line.length; i++) {
    const c = line[i]!;
    if (escapes(c, quote)) i++;
    else if (quote) {
      if (c === quote) {
        spans.push([start, i]);
        quote = "";
      }
    } else if (c === "'" || c === '"') {
      quote = c;
      start = i;
    }
  }
  if (quote) spans.push([start, line.length]);
  return spans;
}

/** A backslash takes the next character as text, except inside single quotes. */
const escapes = (c: string, quote: string) => c === "\\" && quote !== "'";

/**
 * The delimiter of the first heredoc a line opens, `EOF` in `cat <<'EOF'`.
 * Quoted text isn't one, except inside a `"$(…)"`, where the shell runs it.
 * A here-string (`<<< word`) and a shift (`$((1 << 3))`) have no body.
 */
export function heredocOpened(line: string): string | undefined {
  const spans = quotedSpans(line);
  for (const found of line.matchAll(/(?<!<)<<(?!<)-?\s*(['"]?)([A-Za-z_]\w*)\1/g)) {
    if (inArithmetic(line, found.index)) continue;
    const span = spans.find(([start, end]) => found.index > start && found.index < end);
    const substituted = span && line[span[0]] === '"' && line.slice(span[0], found.index).includes("$(");
    if (!span || substituted) return found[2];
  }
  return undefined;
}

/** Whether a spot on a line sits inside a `$(( … ))`. */
function inArithmetic(line: string, at: number) {
  const before = line.slice(0, at);
  return before.lastIndexOf("$((") > before.lastIndexOf("))");
}

/** The command without its heredoc bodies, which come back in the order they were opened. */
export function heredocs(command: string) {
  const lines = command.split("\n");
  const head: string[] = [];
  const bodies: string[] = [];
  for (let i = 0; i < lines.length; i++) {
    head.push(lines[i]!);
    const delimiter = heredocOpened(lines[i]!);
    if (!delimiter) continue;
    const end = lines.findIndex((l, j) => j > i && l.trim() === delimiter);
    const stop = end === -1 ? lines.length : end;
    bodies.push(lines.slice(i + 1, stop).join("\n"));
    i = stop;
  }
  return { head: head.join("\n"), bodies };
}

/** What joins a command to the one before it. */
export type Joiner = "&&" | "||" | ";" | "\n";

/** The commands of a line, each with what joined it to the one before; splits on ; && || and newlines, minding quotes and `$( … )`. */
export function chain(line: string) {
  const out: { segment: string; joinedBy?: Joiner }[] = [];
  let joinedBy: Joiner | undefined;
  let quote = "";
  // How deep in `$( … )`, whose separators belong to the value it works out.
  let depth = 0;
  let current = "";
  const end = (next: Joiner) => {
    const segment = current.trim();
    current = "";
    // A blank line, or the newline after `&&`, joins nothing.
    if (!segment) return;
    out.push({ segment, ...(joinedBy ? { joinedBy } : {}) });
    joinedBy = next;
  };
  for (let i = 0; i < line.length; i++) {
    const c = line[i]!;
    if (escapes(c, quote)) {
      // A backslash before a newline carries the command on to the next line.
      current += line[i + 1] === "\n" ? " " : line.slice(i, i + 2);
      i++;
      continue;
    }
    if (quote) {
      if (c === quote) quote = "";
      current += c;
      continue;
    }
    if (c === "'" || c === '"') quote = c;
    depth = depthAfter(line, i, depth);
    const pair = line.slice(i, i + 2);
    if (depth) current += c;
    else if (pair === "&&" || pair === "||") {
      end(pair);
      i++;
    } else if (c === ";" || c === "\n") end(c);
    else current += c;
  }
  end(";");
  return out;
}

/** How deep in `$( … )` a line is past its character at `i`; a bare `(` opens a subshell, which splits. */
function depthAfter(line: string, i: number, depth: number) {
  const c = line[i];
  if (c === "(" && (depth || line[i - 1] === "$")) return depth + 1;
  if (c === ")" && depth) return depth - 1;
  return depth;
}

/** The commands of a line, without what joins them. */
export const segments = (line: string) => chain(line).map((link) => link.segment);

/** Words with their quotes taken off. */
export function wordsOf(segment: string) {
  return [...segment.matchAll(/'([^']*)'|"((?:\\.|[^"])*)"|(\S+)/g)].map((m) => m[1] ?? m[2] ?? m[3]!);
}

/** The words that aren't flags, redirections or heredoc marks. */
export const positional = (args: string[]) => args.filter((a) => !/^[-<>]/.test(a));

/** Programs that only trim what the one before them printed; PowerShell's in lowercase, as it ignores case. */
const filters = new Set([
  "head", "tail", "cut", "sort", "uniq", "wc", "awk", "grep", "sed", "tr",
  "select-object", "select", "sort-object", "measure-object", "where-object", "select-string", "out-string",
  "format-table", "format-list",
]);

/** Where a segment's first `|` is, outside quotes and `$( … )`; -1 when it has none. */
export function pipeAt(segment: string) {
  let quote = "";
  let depth = 0;
  for (let i = 0; i < segment.length; i++) {
    const c = segment[i]!;
    if (escapes(c, quote)) i++;
    else if (quote) {
      if (c === quote) quote = "";
    } else if (c === "'" || c === '"') quote = c;
    else {
      depth = depthAfter(segment, i, depth);
      if (!depth && c === "|" && segment[i + 1] !== "|") return i;
    }
  }
  return -1;
}

/** The first stage of a pipeline when what comes after `|` only trims its output. */
export function firstStage(segment: string) {
  const at = pipeAt(segment);
  if (at === -1) return segment;
  const next = segment.slice(at + 1).trim().split(/\s+/)[0]!;
  return filters.has(next.toLowerCase()) ? segment.slice(0, at).trim() : segment;
}

/** What a segment pipes into: `node` in `cat <<EOF | node`. */
export function pipedInto(segment: string) {
  const at = pipeAt(segment);
  return at === -1 ? undefined : segment.slice(at + 1).trim();
}

/** `2>&1`, `>/dev/null`, `&> out.txt`, `>> "a b.log"`. */
const redirect = /(^|\s)[\d&]?>>?\s*(&\d|"[^"]*"|'[^']*'|[^\s&|;<>'"]+)/g;

/**
 * A segment without where it sends its output, but for the targets `keeps`
 * says matter, quotes taken off; a `>` inside quotes is text.
 */
export function withoutRedirects(segment: string, keeps: (target: string) => boolean = () => false) {
  const spans = quotedSpans(segment);
  return segment.replace(redirect, (found, before: string, target: string, at: number) => {
    const quoted = spans.some(([start, end]) => at > start && at < end);
    return quoted || keeps(target.replace(/^(["'])(.*)\1$/, "$2")) ? found : before;
  });
}

/** Where a segment sends its output with `>`, or adds it to with `>>`, quotes taken off. */
export function writesTo(segment: string) {
  const found = /(?:^|\s)(>>?)\s*(?:"([^"]+)"|'([^']+)'|([^\s&|;<>'"]+))/.exec(segment);
  if (!found) return undefined;
  return { path: (found[2] ?? found[3] ?? found[4])!, appends: found[1] === ">>" };
}

/** The last part of a path: a program's name, a file's. */
export const baseName = (path: string) => path.split(/[/\\]/).filter(Boolean).at(-1) ?? path;

/** Words that run the command after them: `sudo`, `npx`, `nohup`. */
export const commandWrappers = new Set(["sudo", "env", "time", "nice", "nohup", "exec", "command", "npx", "pnpx", "bunx"]);

/** Commands that only set the shell up for the ones after them. */
export const commandSetup = new Set([
  "cd", "pushd", "popd", "export", "unset", "set", "source", ".", "true", "[", "[[", "test", "done", "fi", "esac", "}",
]);

// Shell grammar around the commands: `if` and `do` lead into one, while the
// headers of `for f in …` or `case $x in` run nothing themselves.
export const shellLeads = new Set(["if", "elif", "while", "until", "then", "do", "else", "!", "{"]);

export const shellHeaders = new Set(["for", "case", "select", "function"]);
