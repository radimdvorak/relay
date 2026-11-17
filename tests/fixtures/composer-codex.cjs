// Catalog-only Codex for composer UI scenarios; no agent turn is started.
// Models in the JSON file at COMPOSER_CODEX_ROLLOUT are listed too, like ones
// OpenAI rolls out to a CLI that is already installed.
const { appendFileSync, existsSync, readFileSync } = require("node:fs");
const { join } = require("node:path");
const discovery = new Set([
  "initialize",
  "account/read",
  "model/list",
  "config/read",
  "skills/list",
]);
const send = (id, result) =>
  process.stdout.write(JSON.stringify({ id, result }) + "\n");
const efforts = ["low", "medium", "high", "xhigh"].map((reasoningEffort) => ({
  reasoningEffort,
}));
const rollout = process.env.COMPOSER_CODEX_ROLLOUT;
const rolledOut = () =>
  rollout && existsSync(rollout)
    ? JSON.parse(readFileSync(rollout, "utf8"))
    : [];
require("node:readline")
  .createInterface({ input: process.stdin })
  .on("line", (line) => {
    const { id, method, params = {} } = JSON.parse(line);
    if (id == null) return;
    if (process.env.RELAY_AGENT_CAPTURE)
      appendFileSync(
        process.env.RELAY_AGENT_CAPTURE,
        JSON.stringify({ discovery: discovery.has(method), method }) + "\n",
      );
    if (method === "account/read")
      return send(id, {
        account: { type: "chatgpt" },
        requiresOpenaiAuth: true,
      });
    if (method === "model/list")
      return send(id, {
        nextCursor: null,
        data: [
          {
            id: "gpt-6-sol",
            model: "gpt-6-sol",
            displayName: "GPT-6-Sol",
            isDefault: true,
            supportedReasoningEfforts: efforts,
          },
          ...rolledOut().map((id) => ({
            id,
            model: id,
            displayName: id.toUpperCase(),
            supportedReasoningEfforts: efforts,
          })),
        ],
      });
    if (method === "config/read")
      return send(id, {
        config: { model: "gpt-6-sol", model_reasoning_effort: "medium" },
      });
    if (method === "skills/list") {
      const cwd = params.cwds?.[0] ?? process.cwd();
      return send(id, {
        data: [
          {
            cwd,
            skills: [
              {
                name: "explain",
                description: "Explain this project",
                path: join(cwd, ".agents/skills/explain/SKILL.md"),
                enabled: true,
              },
            ],
          },
        ],
      });
    }
    if (method === "initialize") return send(id, {});
    process.stdout.write(
      JSON.stringify({
        id,
        error: {
          code: -32601,
          message: `Unexpected composer request: ${method}`,
        },
      }) + "\n",
    );
  });
