import { Agent, get, type IncomingMessage } from "node:http";
import type { LookupFunction } from "node:net";
import { localServiceUrl } from "./native-api";

const loopbacks = [
  { address: "127.0.0.1", family: 4 },
  { address: "::1", family: 6 },
];

// Only localhost reaches lookup. Use literal loopbacks even if the system
// resolves that name elsewhere.
const lookup: LookupFunction = (_host, options, callback) => {
  if (options.all) return callback(null, loopbacks);
  callback(null, loopbacks[0].address, loopbacks[0].family);
};

// The global agent can honor proxy settings even for an HTTP loopback URL.
const direct = new Agent({ lookup, autoSelectFamily: true });

type JsonReply = { status: number; body?: unknown };

/** Reads the local daemon directly. Node HTTP returns redirects as replies. */
export function readLocalServiceJson(
  url: URL,
  authorization: string | undefined,
  timeout: number,
): Promise<JsonReply> {
  const local = localServiceUrl(url.href);
  const headers: Record<string, string> = { accept: "application/json" };
  if (authorization) headers.authorization = authorization;
  const signal = AbortSignal.timeout(timeout);
  return new Promise((resolve, reject) => {
    const request = get(
      local,
      { agent: direct, headers, signal },
      (response) => {
        readReply(response).then(resolve, reject);
      },
    );
    request.once("error", reject);
  });
}

async function readReply(response: IncomingMessage): Promise<JsonReply> {
  const status = response.statusCode ?? 0;
  if (status < 200 || status >= 300) {
    response.destroy();
    return { status };
  }

  response.setEncoding("utf8");
  let text = "";
  for await (const chunk of response) {
    text += chunk;
    if (text.length > 4_000_000)
      throw new Error("OpenCode's response was too large.");
  }
  try {
    return { status, body: JSON.parse(text) };
  } catch {
    return { status };
  }
}
