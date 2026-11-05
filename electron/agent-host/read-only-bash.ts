// A reviewer's Bash, held to commands Relay knows only read. Claude Code's
// own read-only check runs after this, but a permission allow rule in the
// user's settings approves a call before canUseTool can turn it down, so the
// line has to be drawn in a PreToolUse hook, which runs first.

// Not `file`: `file -C` compiles a magic file and writes it beside it.
export const plain = new Set([
  "cd",
  "pwd",
  "ls",
  "cat",
  "head",
  "tail",
  "wc",
  "stat",
  "du",
  "echo",
  "true",
  "which",
  "basename",
  "dirname",
  "realpath",
  "nl",
  "cut",
  "tr",
  "diff",
  "jq",
  "grep",
  "egrep",
  "fgrep",
]);

export const gitReads = new Set([
  "status",
  "diff",
  "log",
  "show",
  "blame",
  "grep",
  "ls-files",
  "ls-tree",
  "rev-parse",
  "rev-list",
  "cat-file",
  "merge-base",
  "describe",
  "shortlog",
  "diff-tree",
  "show-ref",
  "for-each-ref",
  "name-rev",
  "range-diff",
]);

/** Subcommands that only read with these flags and nothing else. */
export const gitListings: Record<string, Set<string>> = {
  branch: new Set([
    "-a",
    "-r",
    "-v",
    "-vv",
    "--all",
    "--remotes",
    "--list",
    "--show-current",
    "--no-color",
  ]),
  remote: new Set(["-v"]),
  stash: new Set(["list"]),
};

/** Listings that change something when given no arguments: a bare `git stash` stashes. */
export const gitBareWrites = new Set(["stash"]);

/**
 * Spellings the shell turns into a flag that a look at the words misses: a
 * quote or backslash before or inside its name (`'--output=x'`, `-de''lete`),
 * a glob or brace right after it, brace expansion (`{-delete,-true}`) and `$`
 * expansions (`${X:--delete}`, `$'\x2ddelete'`). A `$` ending a word, as in
 * `rg 'foo$'`, and sed's `'1,$p'` stay allowed.
 */
const respellings = [
  `(?:^|\\s)['"\\\\]+-`,
  `(?:^|\\s)-[\\w-]*['"\\\\]+[\\w-]`,
  `(?:^|\\s)-[\\w-]*[*?[{]`,
  `\\$(?!['"]?(?:[\\s;&|]|$)|p['"])`,
];

const braceList = `\\{[^\\s}]*(?:,|\\.\\.)`;
export const disguisedFlag = [...respellings, braceList].join("|");
const respelled = new RegExp(respellings.join("|"));
const braces = new RegExp(braceList);
/** A `$` mid-word: bash's `${x@P}` and `$[x]`, or zsh's `${(e)x}`, run what `x` holds. */
const expansion = new RegExp(respellings[3]!);

// Quotes keep the shell from expanding braces, so `rg -g '*.{ts,tsx}'` reads;
// with a backslash about, a quote may not be one, so any brace counts.
const disguised = {
  test: (segment: string) =>
    respelled.test(segment) ||
    braces.test(
      segment.includes("\\")
        ? segment
        : segment.replace(/'[^']*'|"[^"]*"/g, "''"),
    ),
};

const value = "[^\\s;&|]*";
/** Short flags that may cluster (`-in`), then at most one taking a value (`-A3`, `-t ts`). */
const shorts = (bare: string, valued = "") =>
  `-[${bare}]*${valued ? `(?:[${valued}]${value})?` : ""}`;
const longs = (bare: string[], valued: string[] = []) =>
  [
    `--(?:${bare.join("|")})`,
    ...(valued.length ? [`--(?:${valued.join("|")})(?:=${value})?`] : []),
  ].join("|");

/**
 * The flags each command whose flags decide what it does may take, as regular
 * expression sources for one word; anything else turns the command down, so a
 * flag nobody thought of (`rg --hostname-bin`, `sort --compress-program`)
 * can't run a program the reviewed repository ships. A word not starting
 * with `-` is an argument. Long flags are spelled out whole: a cut-short one
 * (`--out`) isn't on the list.
 */
export const allowedFlags: Record<string, string> = {
  rg: [
    "--",
    // Not -z/--search-zip (it runs decompressors), --pre/--pre-glob or
    // --hostname-bin/--hyperlink-format, which run a program, nor --generate.
    shorts("0.abcFHhIiLlNnoPpqSsUuVvwx", "ABCdEefgjMmrTt"),
    longs(
      [
        "files", "files-with-matches", "files-without-match", "count",
        "count-matches", "type-list", "hidden", "no-ignore", "no-ignore-vcs",
        "no-ignore-parent", "no-ignore-dot", "no-ignore-global", "no-config",
        "no-heading", "heading", "line-number", "no-line-number", "column",
        "no-column", "ignore-case", "smart-case", "case-sensitive",
        "fixed-strings", "word-regexp", "line-regexp", "invert-match",
        "multiline", "multiline-dotall", "follow", "text", "null",
        "null-data", "only-matching", "vimgrep", "trim", "pcre2", "json",
        "stats", "with-filename", "no-filename", "quiet", "passthru",
        "no-messages", "crlf", "binary", "byte-offset", "one-file-system",
        "unrestricted", "glob-case-insensitive", "no-require-git", "pretty",
        "max-columns-preview", "no-unicode", "debug", "trace", "version",
        "help", "mmap", "stop-on-nonmatch", "auto-hybrid-regex",
        "ignore-file-case-insensitive", "no-ignore-exclude", "no-ignore-files",
        "no-ignore-messages", "block-buffered", "line-buffered",
        "no-context-separator", "include-zero", "sort-files", "pcre2-version",
        "no-pcre2-unicode",
        // The negations only turn a behaviour back off.
        "no-hidden", "no-follow", "no-text", "no-binary", "no-multiline",
        "no-multiline-dotall", "no-fixed-strings", "no-invert-match",
        "no-pcre2", "no-crlf", "no-mmap", "no-search-zip", "no-sort-files",
        "no-max-columns-preview", "no-glob-case-insensitive",
        "no-ignore-file-case-insensitive", "no-include-zero",
        "no-block-buffered", "no-line-buffered", "no-stats", "no-json",
        "no-trim", "no-byte-offset", "no-one-file-system",
        "no-auto-hybrid-regex", "no-encoding",
      ],
      [
        "after-context", "before-context", "context", "max-count",
        "max-depth", "glob", "iglob", "type", "type-not", "regexp", "file",
        "sort", "sortr", "color", "colors", "max-columns", "max-filesize",
        "replace", "encoding", "threads", "engine", "path-separator",
        "field-match-separator", "field-context-separator", "type-add",
        "type-clear", "ignore-file", "context-separator", "dfa-size-limit",
        "regex-size-limit",
      ],
    ),
  ].join("|"),
  // Not -o, which writes, nor --compress-program, -T or --files0-from.
  sort: [
    shorts("bcCdfghiMmnRrsuVz", "kSt"),
    longs(
      [
        "ignore-leading-blanks", "dictionary-order", "ignore-case",
        "general-numeric-sort", "human-numeric-sort", "ignore-nonprinting",
        "month-sort", "numeric-sort", "random-sort", "reverse",
        "version-sort", "unique", "stable", "merge", "zero-terminated",
        "version", "help",
      ],
      ["key", "field-separator", "buffer-size", "parallel", "sort", "check"],
    ),
  ].join("|"),
  // Not -o, which writes, nor -R, which writes a listing into each folder.
  tree: [
    shorts("acdfghilnpqstuvxACDFJNQSUX", "ILP"),
    longs(
      ["noreport", "dirsfirst", "gitignore", "prune", "du", "si", "matchdirs",
        "ignore-case", "version", "help"],
      ["charset", "filelimit", "sort"],
    ),
  ].join("|"),
  // Tests and actions by name; a number like `-mtime -1` is a test's value.
  find: [
    "-\\d[\\w.+-]*",
    `-(?:${[
      "name", "iname", "path", "ipath", "wholename", "iwholename", "lname",
      "ilname", "regex", "iregex", "type", "xtype", "maxdepth", "mindepth",
      "depth", "prune", "newer", "anewer", "cnewer", "samefile", "mtime",
      "mmin", "atime", "amin", "ctime", "cmin", "size", "empty", "perm",
      "user", "group", "uid", "gid", "nouser", "nogroup", "links", "inum",
      "readable", "writable", "executable", "not", "a", "and", "o", "or",
      "true", "false", "print", "print0", "printf", "ls", "quit", "follow",
      "xdev", "mount", "H", "L", "P", "E", "X", "d", "s", "x",
    ].join("|")})`,
  ].join("|"),
};

const flagRules = Object.fromEntries(
  Object.entries(allowedFlags).map(([name, flags]) => [
    name,
    new RegExp(`^(?:${flags})$`),
  ]),
);

/** Whether every command in `command` is on Relay's list of ones that only read. */
export function readsOnly(command: string): boolean {
  const bare = command.replace(
    /(^|\s)(?:[12]?>\s*\/dev\/null|2>&1)(?=\s|$)/g,
    "$1",
  );
  // Writes, substitutions and here-documents; a quoted one counts too and
  // the command is turned down.
  if (/[<>`]|\$\(/.test(bare)) return false;
  const commands = commandsIn(bare);
  return !!commands && commands.every((c) => !!c && segmentReads(c));
}

/**
 * The commands of `line`, split where bash and zsh split them: a `;`, `|` or
 * newline inside quotes doesn't end one, and a backslash-newline joins two
 * lines. Undefined for what no read needs and this doesn't follow: a
 * subshell or zsh's `*(e:…:)` and `=(…)`, which run code, a command sent to
 * the background, or an unclosed quote.
 */
function commandsIn(line: string): string[] | undefined {
  const commands: string[] = [];
  let text = "";
  let wordStart = true;
  /** How far a comment has been checked for quotes, so each line is read once. */
  let commentChecked = -1;
  const endCommand = () => {
    commands.push(text.trim());
    text = "";
  };
  for (let i = 0; i < line.length; i++) {
    const c = line[i]!;
    const next = line[i + 1];
    const atWordStart = wordStart;
    wordStart = c === " " || c === "\t";
    if (c === "\n" || c === ";") {
      endCommand();
      wordStart = true;
    } else if (c === "|" || c === "&") {
      const doubled = next === c;
      if (!doubled && (c === "&" || next === "&")) return undefined;
      if (doubled) i++;
      endCommand();
      wordStart = true;
    } else if (c === "(" || c === ")") {
      return undefined;
    } else if (c === "\\" && next === "\n") {
      // Removed, as if the two lines were one.
      i++;
      wordStart = atWordStart;
    } else if (c === "\\") {
      text += c + (next ?? "");
      i++;
    } else if (c === "'" || c === '"' || (c === "$" && next === "'")) {
      const ansi = c === "$";
      const close = quoteEnd(line, ansi ? i + 1 : i, ansi || c === '"');
      if (close < 0) return undefined;
      text += line.slice(i, close + 1);
      i = close;
    } else {
      // A comment where the shell has them on, words where it hasn't: a quote
      // in it would split the line differently between the two.
      if (c === "#" && atWordStart && i > commentChecked) {
        const eol = line.indexOf("\n", i);
        commentChecked = eol < 0 ? line.length : eol;
        if (/['"\\]/.test(line.slice(i, commentChecked))) return undefined;
      }
      text += c;
    }
  }
  endCommand();
  return commands;
}

/** Where the quote opening at `open` closes, or -1. */
function quoteEnd(line: string, open: number, escapes: boolean) {
  const quote = line[open]!;
  for (let i = open + 1; i < line.length; i++) {
    if (escapes && line[i] === "\\") i++;
    else if (line[i] === quote) return i;
  }
  return -1;
}

function segmentReads(segment: string) {
  const [name, ...args] = segment.split(/\s+/);
  if (expansion.test(segment)) return false;
  // Their flags decide whether they write, so a flag has to be spelled plainly.
  // GNU sed reads a flag anywhere, so a quoted `'-i'` after the script still edits.
  const flagged = ["git", "sed", ...Object.keys(allowedFlags)];
  if (flagged.includes(name!) && disguised.test(segment)) return false;
  const flags = flagRules[name];
  if (flags) return args.every((a) => !a.startsWith("-") || flags.test(a));
  switch (name) {
    case "git":
      return gitReadsOnly(args);
    case "sed":
      return sedPrints(args);
    default:
      return plain.has(name);
  }
}

/** `git` with these arguments, as words after `git`, only reads. */
export function gitReadsOnly(args: string[]) {
  let i = 0;
  while (args[i] === "--no-pager" || args[i] === "-C")
    i += args[i] === "-C" ? 2 : 1;
  const [sub, ...rest] = args.slice(i);
  if (
    // git takes a long flag cut short (`--open-fi`) and clustered short ones (`-lO`).
    rest.some((a) => /^(--(ou|op|ext)|-[^-]*O)/.test(a))
  )
    return false;
  if (gitReads.has(sub)) return true;
  const listing = gitListings[sub];
  if (!listing || (gitBareWrites.has(sub) && !rest.length)) return false;
  return rest.every((a) => listing.has(a));
}

/** `sed -n '12,40p' file`: printing lines, never editing in place. */
function sedPrints(args: string[]) {
  const flags = args.filter((a) => a.startsWith("-"));
  const [script] = args.filter((a) => !a.startsWith("-"));
  return (
    flags.length > 0 &&
    flags.every((f) => f === "-n") &&
    /^(['"]?)(\d+|\$)(,(\d+|\$))?p\1$/.test(script ?? "")
  );
}

/** The PreToolUse hook: anything off the list is turned down before allow rules can approve it. */
export async function readOnlyBashHook(input: unknown) {
  const { tool_name, tool_input } = (input ?? {}) as {
    tool_name?: string;
    tool_input?: { command?: unknown };
  };
  if (tool_name !== "Bash") return {};
  const command = tool_input?.command;
  if (typeof command === "string" && readsOnly(command)) return {};
  return {
    hookSpecificOutput: {
      hookEventName: "PreToolUse" as const,
      permissionDecision: "deny" as const,
      permissionDecisionReason:
        "This thread only reads the code. Use Read, Grep and Glob, or a plain read-only command like git diff, git log or rg, and report what should change instead of changing files.",
    },
  };
}
