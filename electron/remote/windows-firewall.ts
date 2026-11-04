import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { z } from "zod";

/**
 * Windows Firewall between phones and Relay. Windows asks whether to let an
 * app in the first time it listens; Cancel there leaves block rules behind,
 * and Windows never asks again, so Relay looks for itself.
 */

const run = promisify(execFile);

const reportSchema = z.object({
  rules: z.array(
    z.object({ action: z.string(), profile: z.string(), protocol: z.string() }),
  ),
  networks: z.array(
    z.object({ host: z.string(), category: z.string().nullable() }),
  ),
  profiles: z.array(
    z.object({ name: z.string(), enabled: z.string(), inbound: z.string() }),
  ),
});

export type FirewallReport = z.infer<typeof reportSchema>;

// Windows names a network's category and the firewall profile it gets differently.
const profileOf = (category: string | null) => {
  if (category === "DomainAuthenticated") return "Domain";
  if (category === "Private") return "Private";
  // A network Windows couldn't identify gets the Public profile.
  return "Public";
};

/** The hosts phones are told to use that the firewall keeps them away from. */
export function blockedHosts(report: FirewallReport): string[] {
  return report.networks
    .filter((network) => !letsIn(report, profileOf(network.category)))
    .map((network) => network.host);
}

function letsIn(report: FirewallReport, profile: string) {
  const firewall = report.profiles.find((p) => p.name === profile);
  if (firewall?.enabled === "False") return true;
  const rules = report.rules.filter(
    (rule) =>
      (rule.profile === "Any" || rule.profile.split(", ").includes(profile)) &&
      (rule.protocol === "Any" || rule.protocol === "TCP"),
  );
  // A block rule beats every allow rule.
  if (rules.some((rule) => rule.action === "Block")) return false;
  if (rules.some((rule) => rule.action === "Allow")) return true;
  return firewall?.inbound === "Allow";
}

const quoted = (text: string) => `'${text.replaceAll("'", "''")}'`;

// The rules naming Relay's program, however the path was written.
const relayRules = (store: string) => `$exe = ${quoted(process.execPath)}
$relayRules = Get-NetFirewallApplicationFilter -PolicyStore ${store} |
  Where-Object { $_.Program -and [Environment]::ExpandEnvironmentVariables($_.Program) -ieq $exe } |
  Get-NetFirewallRule |
  Where-Object { $_.Enabled -eq 'True' -and $_.Direction -eq 'Inbound' }`;

const reportScript = (hosts: string[]) => `$ErrorActionPreference = 'Stop'
${relayRules("ActiveStore")}
$rules = @($relayRules | ForEach-Object {
  @{ action = "$($_.Action)"; profile = "$($_.Profile)"; protocol = "$(($_ | Get-NetFirewallPortFilter).Protocol)" }
})
$networks = @(@(${hosts.map(quoted).join(", ")}) | ForEach-Object {
  $ip = Get-NetIPAddress -IPAddress $_ -ErrorAction SilentlyContinue | Select-Object -First 1
  $net = if ($ip) { Get-NetConnectionProfile -InterfaceIndex $ip.InterfaceIndex -ErrorAction SilentlyContinue | Select-Object -First 1 }
  @{ host = $_; category = if ($net) { "$($net.NetworkCategory)" } else { $null } }
})
$profiles = @(Get-NetFirewallProfile -PolicyStore ActiveStore | ForEach-Object {
  @{ name = "$($_.Name)"; enabled = "$($_.Enabled)"; inbound = "$($_.DefaultInboundAction)" }
})
@{ rules = $rules; networks = $networks; profiles = $profiles } | ConvertTo-Json -Depth 4 -Compress`;

// Clears the blocks Cancel left and lets Relay in on every network: phones still need a pairing to get anywhere.
const allowScript = () => `$ErrorActionPreference = 'Stop'
${relayRules("PersistentStore")}
$relayRules | Where-Object { $_.Action -eq 'Block' } | Remove-NetFirewallRule
Get-NetFirewallRule -Name 'Relay-phones' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -Name 'Relay-phones' -DisplayName 'Relay phone access' -Direction Inbound -Program $exe -Protocol TCP -Action Allow -Profile Any | Out-Null`;

const encoded = (script: string) =>
  Buffer.from(script, "utf16le").toString("base64");

const powershell = (script: string, timeout = 30_000) =>
  run(
    "powershell.exe",
    ["-NoProfile", "-NonInteractive", "-EncodedCommand", encoded(script)],
    { windowsHide: true, timeout },
  );

/** Where the firewall keeps phones out; empty off Windows and when nothing's in the way. */
export async function firewallBlocks(hosts: string[]): Promise<string[]> {
  if (process.platform !== "win32" || !hosts.length) return [];
  const { stdout } = await powershell(reportScript(hosts));
  return blockedHosts(reportSchema.parse(JSON.parse(stdout)));
}

// ERROR_CANCELLED, what Start-Process throws when the admin prompt is declined.
const declined = 1223;
// The admin prompt waits on the user: killed under it, a slow Yes would
// still change the firewall after Relay said it failed.
const promptTimeout = 10 * 60_000;

/** Lets Relay in after Windows' admin prompt; false when that was declined. */
export async function allowThroughFirewall(): Promise<boolean> {
  const elevated = `try {
  $p = Start-Process powershell.exe -Verb RunAs -Wait -PassThru -WindowStyle Hidden -ArgumentList '-NoProfile', '-NonInteractive', '-EncodedCommand', '${encoded(allowScript())}'
  exit $p.ExitCode
} catch {
  for ($e = $_.Exception; $e; $e = $e.InnerException) { if ($e.NativeErrorCode -eq ${declined}) { exit ${declined} } }
  throw
}`;
  try {
    await powershell(elevated, promptTimeout);
    return true;
  } catch (error) {
    if ((error as { code?: unknown }).code === declined) return false;
    throw new Error("Windows Firewall didn't take the change.", {
      cause: error,
    });
  }
}
