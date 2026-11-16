// Sealing a secret with the OS credential store (the Keychain, DPAPI, the
// desktop's keyring), so what Relay saves of it is unreadable off this
// account. A headless Relay seals with its own key file instead.
import { safeStorage } from "electron";

/** Linux's basic_text backend stores plaintext; secrets then stay in memory. */
export const canSeal = async () =>
  process.platform === "linux"
    ? safeStorage.isEncryptionAvailable() &&
      safeStorage.getSelectedStorageBackend() !== "basic_text"
    : await safeStorage.isAsyncEncryptionAvailable();

/** Encrypts with the OS credential store; null when it can't persist safely. */
export const seal = async (value: string) =>
  (await canSeal())
    ? (await safeStorage.encryptStringAsync(value)).toString("base64")
    : null;

export const unseal = async (value: string) =>
  (await safeStorage.decryptStringAsync(Buffer.from(value, "base64"))).result;
