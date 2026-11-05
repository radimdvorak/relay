import { describe, expect, it } from "vitest";
import { redactSecrets } from "./redact-secrets";

// Joined at runtime so secret scanners don't flag these fixtures in the repo.
const fake = (...parts: string[]) => parts.join("");
const github = fake("gh", "p_", "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8");
const aws = fake("AK", "IA", "Z7Q2XK4M9WPL3RTB");
const slack = fake("xo", "xb-", "1234567890-0987654321-AbCdEfGhIjKl");
const anthropic = fake("sk-", "ant-", "api03-Xy9Wv8Ut7Sr6Qp5On4Ml3Kj2");
const openai = fake("sk-", "proj-", "Zx8Cv7Bn6Mm5Ll4Kk3Jj2Hh1Gg0");

const jwt = fake(
  "eyJ",
  "hbGciOiJIUzI1NiJ9",
  ".eyJ",
  "zdWIiOiIxMjM0NTY3ODkwIn0",
  ".dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U",
);

const pem = [
  fake("-----BEGIN ", "RSA PRIVATE KEY-----"),
  "MIIEowIBAAKCAQEAu1SU1LfVLPHCozMxH2Mo4lgOEePzNm0tRgeLezV6ffAt0gun",
  "VTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u+qKhbwKfBstIs+bMY2Zkp18gnTxK",
  fake("-----END ", "RSA PRIVATE KEY-----"),
].join("\n");

describe("redactSecrets", () => {
  it("names each key it removes and keeps the prose around it", () => {
    const { text, found } = redactSecrets(
      [
        `Push failed with ${github}, so I tried the AWS key ${aws}.`,
        `The bot posts with ${slack}; Claude uses ${anthropic} and Codex ${openai}.`,
        `Session cookie: ${jwt}`,
        "```",
        pem,
        "```",
      ].join("\n"),
    );
    expect(text).toBe(
      [
        "Push failed with [redacted GitHub token], so I tried the AWS key [redacted AWS key].",
        "The bot posts with [redacted Slack token]; Claude uses [redacted Anthropic key] and Codex [redacted OpenAI key].",
        "Session cookie: [redacted JWT]",
        "```",
        "[redacted private key]",
        "```",
      ].join("\n"),
    );
    expect(found).toBe(7);
  });

  it("keeps variable names and hosts, dropping only their values", () => {
    const { text } = redactSecrets(
      [
        "```sh",
        `export GITHUB_TOKEN=${github}`,
        "DB_PASSWORD=hunter2hunter2",
        "DATABASE_URL=postgres://relay:s3cr3t-pass@db.internal:5432/relay",
        "curl -H 'Authorization: Bearer abcdef0123456789abcdef' https://api.example.com",
        "```",
        'const config = { apiKey: "live-9f8e7d6c5b4a" };',
      ].join("\n"),
    );
    expect(text).toBe(
      [
        "```sh",
        "export GITHUB_TOKEN=[redacted GitHub token]",
        "DB_PASSWORD=[redacted]",
        "DATABASE_URL=postgres://relay:[redacted]@db.internal:5432/relay",
        "curl -H 'Authorization: Bearer [redacted]' https://api.example.com",
        "```",
        'const config = { apiKey: "[redacted]" };',
      ].join("\n"),
    );
  });

  it("cuts a truncated private key off at its code fence", () => {
    const head = pem.split("\n").slice(0, 2).join("\n");
    expect(
      redactSecrets(`\`\`\`\n${head}\n\`\`\`\nThat key is expired.`),
    ).toEqual({
      text: "```\n[redacted private key]\n```\nThat key is expired.",
      found: 1,
    });
  });

  it("leaves hashes, ids, config names and references alone", () => {
    const text = [
      "Commit 38edd0e5c1a4b7f2d9e8c3b6a1f4d7e2c5b8a9f0 fixed it; request 1b4e28ba-2fa1-11d2-883f-0016cb4b0e5d.",
      "Install scikit-learn, not sk-learn, and check task-queue-worker-shutdown-timeout.",
      "MAX_TOKENS=4096",
      "TOKEN_URL=https://auth.example.com/oauth/token",
      "API_KEY=process.env.API_KEY",
      'token: "${{ secrets.GITHUB_TOKEN }}"',
      'password: "<your password>"',
      "Open http://127.0.0.1:5177/previews/share.html and use Bearer tokens.",
    ].join("\n");
    expect(redactSecrets(text)).toEqual({ text, found: 0 });
  });

  it("drops a token used as a bare URL username", () => {
    const pat = fake("x7k2m9q4w8e3r5t6y1u0i2o4p6a8s0d2", "f4g6h8j0k2l4");
    const { text, found } = redactSecrets(
      `git clone https://${pat}@dev.azure.com/acme/relay/_git/relay`,
    );
    expect(text).toBe(
      "git clone https://[redacted]@dev.azure.com/acme/relay/_git/relay",
    );
    expect(found).toBe(1);
  });

  it("keeps plain URL usernames", () => {
    const text = [
      "ssh://git@github.com/acme/relay.git",
      "https://jane.doe@dev.azure.com/acme/_git/relay",
      "https://acme-engineering-platform@dev.azure.com/acme/_git/relay",
    ].join("\n");
    expect(redactSecrets(text)).toEqual({ text, found: 0 });
  });

  it("drops the value of an API key header", () => {
    const { text } = redactSecrets(
      [
        "curl -H 'X-API-Key: 9f8e7d6c5b4a3f2e1d0c' https://api.example.com",
        "X-Api-Key: 9f8e7d6c5b4a3f2e1d0c",
        'curl -H "PRIVATE-TOKEN: 9f8e7d6c5b4a3f2e1d0c" https://gitlab.example.com',
        "Ocp-Apim-Subscription-Key: 9f8e7d6c5b4a3f2e1d0c",
      ].join("\n"),
    );
    expect(text).toBe(
      [
        "curl -H 'X-API-Key: [redacted]' https://api.example.com",
        "X-Api-Key: [redacted]",
        'curl -H "PRIVATE-TOKEN: [redacted]" https://gitlab.example.com',
        "Ocp-Apim-Subscription-Key: [redacted]",
      ].join("\n"),
    );
  });

  it("keeps code that names an API key header or field", () => {
    const text = [
      "const client = new OpenAI({ apiKey: config.openaiApiKey });",
      'headers: { "x-api-key": this.options.apiKey },',
      "X-Request-Id: 3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6a7b",
    ].join("\n");
    expect(redactSecrets(text)).toEqual({ text, found: 0 });
  });

  it("drops a quoted subscription key", () => {
    const json = '{ "Ocp-Apim-Subscription-Key": "9f8e7d6c5b4a3f2e1d0c" }';
    expect(redactSecrets(json).text).toBe(
      '{ "Ocp-Apim-Subscription-Key": "[redacted]" }',
    );
  });

  it("takes linear time on long runs of near-secrets", () => {
    const inputs = [
      "https://" + "a".repeat(50_000),
      "https://a" + ":a".repeat(25_000),
      "X-API-Key: " + "a".repeat(50_000) + "\n",
      "Authorization: Bearer " + "a".repeat(50_000),
      "-----BEGIN PRIVATE KEY-----" + "a\n".repeat(25_000),
      "A_".repeat(25_000) + "TOKEN",
      "token".repeat(10_000),
    ];
    for (const input of inputs) {
      const started = performance.now();
      redactSecrets(input);
      expect(performance.now() - started).toBeLessThan(500);
    }
  });
});
