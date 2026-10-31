import { z } from "zod";
import { phoneAppearanceSchema } from "../remote/phone-remote";
import { takes, type ApiContext } from "./context";

/** Settings → Phone: letting phones in and pairing them. */
export function phoneRemoteHandlers(ctx: ApiContext) {
  function requirePhoneRemote() {
    const phoneRemote = ctx.phoneRemote();
    if (!phoneRemote) throw new Error("Relay is still starting.");
    return phoneRemote;
  }

  return {
    phoneRemoteState: () => requirePhoneRemote().state(),
    setPhoneRemote: takes([z.boolean()], (enabled) =>
      requirePhoneRemote().setEnabled(enabled),
    ),
    setPhoneRemoteLan: takes([z.boolean()], (lan) =>
      requirePhoneRemote().setLan(lan),
    ),
    phonePairing: () => requirePhoneRemote().pairing(),
    phoneAppearance: takes([phoneAppearanceSchema], (appearance) =>
      requirePhoneRemote().setAppearance(appearance),
    ),
    revokePhone: takes([z.uuid()], (id) => requirePhoneRemote().revoke(id)),
  };
}
