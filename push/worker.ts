// Relay's push service: takes a computer's push for a phone and hands it to
// Firebase, which only Relay's own key may do. It keeps and logs nothing it's
// sent. Deploy: npm run deploy:push; the key goes in with
// `wrangler secret put FIREBASE_SERVICE_ACCOUNT --config push/wrangler.jsonc`.
import { firebaseRequest, pushMessageFrom } from "../shared/push";

interface RateLimit {
  limit(options: { key: string }): Promise<{ success: boolean }>;
}

interface Env {
  /** The Firebase service account's JSON key. */
  FIREBASE_SERVICE_ACCOUNT: string;
  PER_PHONE: RateLimit;
  PER_ADDRESS: RateLimit;
}

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

const maxBody = 8 * 1024;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/send")
      return new Response("Not found", { status: 404 });
    const address = request.headers.get("cf-connecting-ip") ?? "unknown";
    if (!(await env.PER_ADDRESS.limit({ key: address })).success)
      return new Response("Too many", { status: 429 });
    const text = await request.text();
    if (text.length > maxBody) return new Response("Too big", { status: 413 });
    const message = pushMessageFrom(parsed(text));
    if (!message) return new Response("Bad push", { status: 400 });
    if (!(await env.PER_PHONE.limit({ key: message.token })).success)
      return new Response("Too many", { status: 429 });
    const account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT) as ServiceAccount;
    const sent = await fetch(
      `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
      {
        method: "POST",
        headers: {
          authorization: `Bearer ${await accessToken(account)}`,
          "content-type": "application/json",
        },
        body: JSON.stringify(firebaseRequest(message)),
      },
    );
    if (sent.ok) return new Response(null, { status: 204 });
    // The app was uninstalled or its token rotated: the computer forgets it.
    if (await unregistered(sent)) return new Response("Gone", { status: 410 });
    return new Response("Firebase refused", { status: 502 });
  },
};

function parsed(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

async function unregistered(response: Response) {
  if (response.status === 404) return true;
  const body = await response.text().catch(() => "");
  return body.includes("UNREGISTERED");
}

let cached: { token: string; expiresAt: number } | undefined;

/** An OAuth token for Firebase, minted from the service account and kept until near its end. */
async function accessToken(account: ServiceAccount) {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.expiresAt > now + 60) return cached.token;
  const assertion = await signedJwt(account, now);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok)
    throw new Error(`Google refused the key: ${response.status}`);
  const { access_token, expires_in } = (await response.json()) as {
    access_token: string;
    expires_in: number;
  };
  cached = { token: access_token, expiresAt: now + expires_in };
  return access_token;
}

async function signedJwt(account: ServiceAccount, now: number) {
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const unsigned = `${base64Url(JSON.stringify(header))}.${base64Url(JSON.stringify(claims))}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemBytes(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  return `${unsigned}.${base64Url(new Uint8Array(signature))}`;
}

function pemBytes(pem: string) {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
}

function base64Url(input: string | Uint8Array) {
  const bytes =
    typeof input === "string" ? new TextEncoder().encode(input) : input;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}
