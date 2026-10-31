import { z } from "zod";
import { phoneAppearanceSchema } from "../remote/phone-remote";
import {
  allowThroughFirewall,
  firewallBlocks,
} from "../remote/windows-firewall";
import { takes, type ApiContext } from "./context";

/** Settings → Phone: letting phones in and pairing them. */
export function phoneRemoteHandlers(ctx: ApiContext) {
  function requirePhoneRemote() {
    const phoneRemote = ctx.phoneRemote();
    if (!phoneRemote) throw new Error("Relay is still starting.");
    return phoneRemote;
  }

  async function phoneFirewall() {
    const state = await requirePhoneRemote().state();
    if (!state.listening) return { blocked: [] };
    const blocked = await firewallBlocks(state.hosts).catch((error) => {
      // Not knowing mustn't stop pairing; the phone says when it can't connect.
      console.warn("Phone remote: couldn't read Windows Firewall:", error);
      return [];
    });
    return { blocked };
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
    phoneFirewall,
    allowPhonesThroughFirewall: () => allowThroughFirewall(),
  };
}
