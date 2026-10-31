import { describe, expect, it } from "vitest";
import { blockedHosts, type FirewallReport } from "./windows-firewall";

const firewallOn = ["Domain", "Private", "Public"].map((name) => ({
  name,
  enabled: "True",
  inbound: "NotConfigured",
}));

// A PC on a home network Windows calls Public, and on Tailscale, which it calls Private.
const networks = [
  { host: "192.168.0.140", category: "Public" },
  { host: "100.71.30.95", category: "Private" },
];

const report = (
  rules: FirewallReport["rules"],
  profiles = firewallOn,
): FirewallReport => ({ rules, networks, profiles });

describe("blockedHosts", () => {
  it("blocks every network after Cancel on Windows' prompt", () => {
    // Cancel leaves block rules for the network it was asked on; the rest has no rule at all.
    const cancelled = report([
      { action: "Block", profile: "Private", protocol: "TCP" },
      { action: "Block", profile: "Private", protocol: "UDP" },
    ]);

    expect(blockedHosts(cancelled)).toEqual(["192.168.0.140", "100.71.30.95"]);
  });

  it("lets phones in on every network once Relay is allowed", () => {
    const allowed = report([
      { action: "Allow", profile: "Any", protocol: "TCP" },
    ]);

    expect(blockedHosts(allowed)).toEqual([]);
  });

  it("keeps a block when an allow rule covers the same network", () => {
    const both = report([
      { action: "Allow", profile: "Any", protocol: "TCP" },
      { action: "Block", profile: "Domain, Private", protocol: "Any" },
    ]);

    expect(blockedHosts(both)).toEqual(["100.71.30.95"]);
  });

  it("ignores a UDP block and a firewall that's off", () => {
    const publicOff = firewallOn.map((p) =>
      p.name === "Public" ? { ...p, enabled: "False" } : p,
    );
    const udpOnly = report(
      [
        { action: "Block", profile: "Private", protocol: "UDP" },
        { action: "Allow", profile: "Private", protocol: "TCP" },
      ],
      publicOff,
    );

    expect(blockedHosts(udpOnly)).toEqual([]);
  });

  it("treats a network Windows couldn't identify as Public", () => {
    const unidentified: FirewallReport = {
      rules: [{ action: "Allow", profile: "Private", protocol: "TCP" }],
      networks: [{ host: "10.0.0.5", category: null }],
      profiles: firewallOn,
    };

    expect(blockedHosts(unidentified)).toEqual(["10.0.0.5"]);
  });
});
