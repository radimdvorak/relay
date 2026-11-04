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

const port = 47821;

const report = (
  rules: FirewallReport["rules"],
  profiles = firewallOn,
): FirewallReport => ({ rules, networks, profiles });

describe("blockedHosts", () => {
  it("blocks every network after Cancel on Windows' prompt", () => {
    // Cancel leaves block rules for the network it was asked on; the rest has no rule at all.
    const cancelled = report([
      {
        action: "Block",
        profile: "Private",
        protocol: "TCP",
        localPorts: "Any",
      },
      {
        action: "Block",
        profile: "Private",
        protocol: "UDP",
        localPorts: "Any",
      },
    ]);

    expect(blockedHosts(cancelled, port)).toEqual([
      "192.168.0.140",
      "100.71.30.95",
    ]);
  });

  it("lets phones in on every network once Relay is allowed", () => {
    const allowed = report([
      { action: "Allow", profile: "Any", protocol: "TCP", localPorts: "Any" },
    ]);

    expect(blockedHosts(allowed, port)).toEqual([]);
  });

  it("keeps a block when an allow rule covers the same network", () => {
    const both = report([
      { action: "Allow", profile: "Any", protocol: "TCP", localPorts: "Any" },
      {
        action: "Block",
        profile: "Domain, Private",
        protocol: "Any",
        localPorts: "Any",
      },
    ]);

    expect(blockedHosts(both, port)).toEqual(["100.71.30.95"]);
  });

  it("ignores a UDP block and a firewall that's off", () => {
    const publicOff = firewallOn.map((p) =>
      p.name === "Public" ? { ...p, enabled: "False" } : p,
    );
    const udpOnly = report(
      [
        {
          action: "Block",
          profile: "Private",
          protocol: "UDP",
          localPorts: "Any",
        },
        {
          action: "Allow",
          profile: "Private",
          protocol: "TCP",
          localPorts: "Any",
        },
      ],
      publicOff,
    );

    expect(blockedHosts(udpOnly, port)).toEqual([]);
  });

  it("counts only rules that cover Relay's port", () => {
    const otherPort = report([
      { action: "Allow", profile: "Any", protocol: "TCP", localPorts: "8080" },
      { action: "Block", profile: "Any", protocol: "TCP", localPorts: "RPC" },
    ]);
    const range = report([
      {
        action: "Allow",
        profile: "Any",
        protocol: "TCP",
        localPorts: "80 47000-48000",
      },
    ]);

    expect(blockedHosts(otherPort, port)).toEqual([
      "192.168.0.140",
      "100.71.30.95",
    ]);
    expect(blockedHosts(range, port)).toEqual([]);
  });

  it("treats a network Windows couldn't identify as Public", () => {
    const unidentified: FirewallReport = {
      rules: [
        {
          action: "Allow",
          profile: "Private",
          protocol: "TCP",
          localPorts: "Any",
        },
      ],
      networks: [{ host: "10.0.0.5", category: null }],
      profiles: firewallOn,
    };

    expect(blockedHosts(unidentified, port)).toEqual(["10.0.0.5"]);
  });
});
