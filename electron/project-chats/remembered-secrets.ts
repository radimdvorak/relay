// The secrets a user answered with Always: kept for the project, sealed with
// the OS credential store like Relay's other credentials, and handed to that
// project's agents under exactly the name they were saved as.
import {
  secretNameKey,
  type RememberedSecret,
  type SavedSecrets,
} from "../../shared/secret-request";
import type { Store } from "../app/store";
import { canSeal, seal, unseal } from "../platform/seal";

type Encrypt = (value: string) => Promise<string | null>;
type Decrypt = (value: string) => Promise<string>;

export class RememberedSecrets {
  /**
   * Where the OS can't seal safely (Linux's plaintext backend) a secret is
   * remembered until Relay quits, by project id then name key.
   */
  private session = new Map<
    string,
    Map<string, RememberedSecret & { value: string }>
  >();

  constructor(
    private store: Pick<Store, "get" | "update">,
    private encrypt: Encrypt = seal,
    private decrypt: Decrypt = unseal,
    private canEncrypt: () => Promise<boolean> = canSeal,
  ) {}

  /** What is remembered for the project, and whether this computer keeps it past a restart. */
  async saved(projectId: string): Promise<SavedSecrets> {
    const kept = await this.canEncrypt();
    return {
      secrets: this.list(projectId),
      ...(kept ? {} : { sessionOnly: true as const }),
    };
  }

  /** What is remembered for the project, oldest first; names only. */
  list(projectId: string): RememberedSecret[] {
    const sealed = Object.values(
      this.store.get().projectSecrets?.[projectId] ?? {},
    ).map(({ name, saved }) => ({ name, saved }));
    const session = [...(this.session.get(projectId)?.values() ?? [])].map(
      ({ name, saved }) => ({ name, saved, sessionOnly: true as const }),
    );
    return [...sealed, ...session].sort((a, b) => a.saved - b.saved);
  }

  async remember(projectId: string, name: string, value: string) {
    const key = secretNameKey(name);
    const saved = Date.now();
    const sealed = await this.encrypt(value);
    await this.forget(projectId, name);
    if (!sealed) {
      const kept = this.session.get(projectId) ?? new Map();
      this.session.set(projectId, kept.set(key, { name, saved, value }));
      return;
    }
    await this.store.update((s) => {
      const secrets = { ...s.projectSecrets?.[projectId] };
      secrets[key] = { name, saved, sealed };
      s.projectSecrets = { ...s.projectSecrets, [projectId]: secrets };
    });
  }

  /**
   * The secret remembered under exactly this name, or undefined: nothing was,
   * or what was can't be read any more, and the user is asked again.
   */
  async value(projectId: string, name: string) {
    const key = secretNameKey(name);
    const kept = this.session.get(projectId)?.get(key);
    if (kept) return kept.value;
    const sealed = this.store.get().projectSecrets?.[projectId]?.[key]?.sealed;
    if (!sealed) return undefined;
    return this.decrypt(sealed).catch((e) => {
      console.warn("Could not read a remembered secret:", e);
      return undefined;
    });
  }

  async forget(projectId: string, name: string) {
    const key = secretNameKey(name);
    this.session.get(projectId)?.delete(key);
    if (!this.store.get().projectSecrets?.[projectId]?.[key]) return;
    await this.store.update((s) => {
      const { [key]: _, ...rest } = s.projectSecrets?.[projectId] ?? {};
      s.projectSecrets = { ...s.projectSecrets, [projectId]: rest };
    });
  }
}

const byStore = new WeakMap<object, RememberedSecrets>();

/** The one set of remembered secrets a store's threads, tools and settings share. */
export function rememberedSecrets(store: Pick<Store, "get" | "update">) {
  const existing = byStore.get(store);
  if (existing) return existing;
  const made = new RememberedSecrets(store);
  byStore.set(store, made);
  return made;
}
