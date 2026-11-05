// The macOS Keychain through `security`, for the sign-ins usage reads refresh.
import { execFile } from "node:child_process";
import { promisify } from "node:util";

const exec = promisify(execFile);

/** Services a refreshed sign-in may be written back to; nothing else is touched. */
const allowedServices = new Set<string>();

export function allowKeychainWrites(service: string) {
  allowedServices.add(service);
}

export async function readKeychain(service: string, account: string | null) {
  const args = ["find-generic-password", "-s", service, "-w"];
  if (account) args.splice(1, 0, "-a", account);
  const result = await security(args);
  if (result.status !== 0) return null;
  return result.stdout.trim() || null;
}

export async function writeKeychain(
  service: string,
  account: string | null,
  value: string,
) {
  if (!allowedServices.has(service)) throw new Error("refusing keychain write");
  const args = ["add-generic-password", "-U", "-s", service, "-w", value];
  if (account) args.splice(2, 0, "-a", account);
  const result = await security(args);
  if (result.status !== 0) throw new Error("keychain update failed");
}

function security(args: string[]) {
  return exec("/usr/bin/security", args, {
    timeout: 12_000,
    maxBuffer: 2 * 1024 * 1024,
  }).then(
    ({ stdout }) => ({ status: 0, stdout: String(stdout) }),
    (error: { status?: number; stdout?: string }) => ({
      status: typeof error.status === "number" ? error.status : 1,
      stdout: String(error.stdout ?? ""),
    }),
  );
}
