import { expect, it } from "vitest";
import { readOnlyBashHook, readsOnly } from "./read-only-bash";

it("lets the commands a reviewer reads with through", () => {
  for (const command of [
    "git status --short",
    "git --no-pager diff main...HEAD -- src",
    "git -C ../other log --oneline -20",
    "git branch --show-current",
    "git stash list",
    "rg -n 'useEffect' src | head -40",
    "cd electron && git diff HEAD~1 2>/dev/null",
    "sed -n '120,180p' src/app.ts",
    "find src -name '*.test.ts'",
    "ls -la; wc -l src/styles.css",
    "rg -n -C3 --glob '*.ts' -t ts useEffect src",
    "rg -iA2 --max-count=5 TODO",
    "sort -t, -k2,2 -rn data.csv",
    "find . -type f -mtime -1 -size +10k -print",
    "tree -L 2 -I node_modules",
    "rg --no-hidden --no-follow --no-multiline -uu TODO",
    "rg --type-add 'web:*.html' -t web --sort-files color",
    "rg --ignore-file .reviewignore --no-ignore-messages --context-separator=-- foo",
    // Quoted, a brace list is a glob for rg, not shell expansion.
    "rg -g '*.{ts,tsx}' useEffect src",
    'find src -name "*.{ts,tsx}"',
  ])
    expect(readsOnly(command), command).toBe(true);
});

it("turns down anything that writes, runs or hides another command", () => {
  for (const command of [
    "touch marker.txt",
    "git checkout -- src",
    "git commit -am wip",
    "git branch -D old",
    "git stash",
    "git -c core.pager=sh log",
    "git diff --output=patch.diff",
    "git status && rm -rf src",
    "rg --pre ./run.sh foo",
    // Flags that run a program the reviewed repository may ship.
    "rg --hostname-bin=./scripts/evil.sh --hyperlink-format='file://{host}{path}' TODO .",
    "rg --hostname-bin ./evil.sh --hyperlink-format file://{host}{path} hello .",
    "rg --pre-glob '*.pdf' --pre ./evil.sh x",
    "rg -z hello .",
    "rg --search-zip hello .",
    "rg {--pre=./evil.sh,TODO} .",
    "rg 'x'{--pre=./evil.sh,y}'z' .",
    "rg \\'{--pre=./evil.sh,a}\\' .",
    "rg --generate=man",
    "rg -nz hello .",
    "sort -S 1000 --compress-program=./scripts/evil.sh package-lock.json",
    "sort --compress-program ./comp.sh big.txt",
    "sort --comp=./comp.sh big.txt",
    "tree -R -H . src",
    "find . -fls /tmp/x",
    "find . -name '*.tmp' -delete",
    "sed -i 's/a/b/' file.ts",
    "sed -n '1p;w out' file.ts",
    "cat a > b",
    "echo $(rm -rf src)",
    "echo `id`",
    "npm test",
    "file -C -m magic",
    "ls | xargs rm",
    "sleep 100 &",
    "FOO=1 git status",
    "",
  ])
    expect(readsOnly(command), command).toBe(false);
});

it("turns down a plain command whose expansion runs another command", () => {
  for (const command of [
    // bash: the assignment stores "$(touch m)", @P expands it as a prompt.
    `echo \${x:=$'\\x24(touch m)'}\${x@P}`,
    `echo \${x:=$'\\x24(touch m)'}\${(e)x}`,
    `echo \${x:=$'a[\\x24(touch m)]'}$[x]`,
  ])
    expect(readsOnly(command), command).toBe(false);
});

it("reads the commands of a line where the shell splits them", () => {
  for (const command of [
    // A separator in quotes is part of rg's argument, so --pre is rg's too.
    "rg foo ';echo x' --pre=./evil.sh .",
    "rg foo '|echo x' --pre=./evil.sh .",
    "find src ';echo x' -delete",
    "rg foo '\necho x' --pre=./evil.sh .",
    // A backslash-newline joins the lines into one command.
    "rg foo \\\necho --pre=./evil.sh .",
    "find src \\\necho -delete",
    "git log --o\\\nutput=pwned",
    // zsh runs the glob qualifier's code, and =(…) like <(…).
    "ls *(e:'touch pwned':)",
    "cat =(touch pwned)",
    // With comments off, the quote would open and swallow the newline.
    "echo #'\ntouch pwned\n'",
    "echo \\\n#'\ntouch pwned\n'",
    // An escaped $ doesn't start $'…', so \' doesn't escape the quote.
    "echo \\$'\\' ;touch pwned\n'",
    "rg foo 'unclosed",
    "ls |& cat",
  ])
    expect(readsOnly(command), command).toBe(false);
  for (const command of [
    "rg -n 'a;b|c' src",
    "rg -n 'a && b' src",
    "rg -n useEffect \\\n  src",
    "find src \\( -name '*.ts' -o -name '*.tsx' \\)",
    "ls # just looking",
  ])
    expect(readsOnly(command), command).toBe(true);
});

it("turns down a wildcard that may expand to a file named like a flag", () => {
  // A repository can ship files named --pre=sh, -i, -o or --output=x.
  for (const command of [
    "rg foo *",
    "rg foo ''*",
    "rg foo ?*",
    "sed -n 1p *",
    "sort *.txt",
    "git diff *",
    "find * -name '*.ts'",
    "tree [a-z]*",
  ])
    expect(readsOnly(command), command).toBe(false);
  for (const command of [
    "rg foo src/*.ts",
    "rg foo ./*",
    "rg foo '*'",
    "rg foo \\*",
    "cat *.md",
    "ls -la *",
  ])
    expect(readsOnly(command), command).toBe(true);
});

it("keeps the git config a read-only turn's environment sets", () => {
  // The turn's env sets safe.bareRepository=explicit, so a committed bare
  // repository's core.fsmonitor doesn't run; each of these would undo it.
  for (const command of [
    "GIT_CONFIG_COUNT=0 git -C fixtures/evil.git status",
    "GIT_CONFIG_PARAMETERS=\"'safe.bareRepository'='all'\" git status",
    "GIT_DIR=fixtures/evil.git git status",
    "env -u GIT_CONFIG_COUNT git -C fixtures/evil.git status",
    "unset GIT_CONFIG_COUNT; git -C fixtures/evil.git status",
    "export GIT_CONFIG_COUNT=0; git -C fixtures/evil.git status",
    "typeset GIT_CONFIG_COUNT=0; git status",
    "declare -x GIT_CONFIG_COUNT=0; git status",
    "echo ${GIT_CONFIG_COUNT:=0} && git status",
    "git -c safe.bareRepository=all -C fixtures/evil.git status",
    "git -C fixtures/evil.git -c safe.bareRepository=all status",
    "git --config-env=safe.bareRepository=X status",
    "git --git-dir=fixtures/evil.git status",
    "git --no-pager --git-dir fixtures/evil.git status",
  ])
    expect(readsOnly(command), command).toBe(false);
});

it("turns down sed flags hidden in quotes, which GNU sed still reads", () => {
  // GNU sed reads '-e' after the script as a flag, and its `e` command runs
  // a shell command; '-i' edits the file in place.
  for (const command of [
    "sed -n '1p' '-e' '1e touch pwned' README.md",
    "sed -n '1p' '-i' src/app.ts",
    "sed -n 1p \\-i src/app.ts",
  ])
    expect(readsOnly(command), command).toBe(false);
  expect(readsOnly("sed -n '1,$p' src/app.ts")).toBe(true);
});

it("leaves other tools to the usual checks", async () => {
  expect(await readOnlyBashHook({ tool_name: "Read", tool_input: {} })).toEqual(
    {},
  );
  expect(
    await readOnlyBashHook({
      tool_name: "Bash",
      tool_input: { command: "touch marker.txt" },
    }),
  ).toMatchObject({ hookSpecificOutput: { permissionDecision: "deny" } });
});
