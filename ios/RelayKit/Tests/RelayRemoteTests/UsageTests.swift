import Foundation
import Testing

@testable import RelayRemote

// The Android app's UsagePaceTest (shared/usage-history.test.ts's "weekly pace
// over working hours") and FormatsAndUsageTest's usage cases.
struct UsageTests {
    let week: Int64 = 7 * 24 * 60 * 60 * 1000
    let prague = TimeZone(identifier: "Europe/Prague")!
    let nineToFive = (0..<24).map { (9...16).contains($0) ? 1.0 : 0.0 }

    /// Local times, since working hours are learned per local hour of the day.
    func at(_ day: Int, _ hour: Int, _ minute: Int = 0, zone: TimeZone? = nil) -> Int64 {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone ?? prague
        let d = cal.date(from: DateComponents(year: 2026, month: 9, day: day, hour: hour, minute: minute))!
        return Int64(d.timeIntervalSince1970 * 1000)
    }

    func weekly(_ used: Double) -> UsageWindow {
        UsageWindow(kind: "weekly", usedPercent: used, resetsAt: at(21, 0) + week, periodMs: week)
    }

    func present(_ w: UsageWindow, _ now: Int64, _ hours: [Double]? = nil) -> UsageMeter {
        presentWindow(w, now: now, activeHours: hours, zone: prague)
    }

    @Test func leavesAModelsOwnLimitOutOfTheRings() throws {
        let json = """
            {"provider":"claude","windows":[
              {"kind":"weekly","usedPercent":50,"resetsAt":null,"periodMs":\(week)},
              {"kind":"weekly","usedPercent":90,"resetsAt":null,"periodMs":\(week),"model":"Fable","label":"Fable · Weekly"}
            ]}
            """
        let usage = try JSONDecoder().decode(ProviderUsage.self, from: Data(json.utf8))
        #expect(ringMeters(usage, now: at(21, 0), zone: prague).map(\.leftPercent) == [50])
        #expect(usageMeters(usage, now: at(21, 0), zone: prague).count == 2)
    }

    @Test func drawsCursorsTightestMonthlyPoolOnTheLongRing() throws {
        let month = 30 * 24 * 60 * 60 * 1000
        let json = """
            {"provider":"cursor","message":null,"windows":[
              {"id":"api","label":"API","kind":"monthly","usedPercent":24,"resetsAt":null,"periodMs":\(month)},
              {"id":"total","label":"Total","kind":"monthly","usedPercent":1,"resetsAt":null,"periodMs":\(month)}
            ]}
            """
        let usage = try JSONDecoder().decode(ProviderUsage.self, from: Data(json.utf8))
        let rings = ringMeters(usage, now: at(21, 0), zone: prague)
        #expect(rings.map(\.label) == ["API"])
        #expect(rings.map(\.leftPercent) == [76])
    }

    @Test func holdsThePaceMarkStillOvernight() {
        let evening = present(weekly(10), at(21, 20), nineToFive)
        let morning = present(weekly(10), at(22, 8), nineToFive)
        #expect(abs(evening.paceLeftPercent! - 48.0 / 56 * 100) < 1e-5)
        #expect(abs(evening.paceLeftPercent! - morning.paceLeftPercent!) < 1e-5)
        // Wall-clock pacing slides it on through the night.
        #expect(present(weekly(10), at(22, 8)).paceLeftPercent! < present(weekly(10), at(21, 20)).paceLeftPercent!)
    }

    @Test func keepsARunOutAmberWhileOnlyALittlePastTheMark() {
        // 2 hours in: the mark is at ~96% left and 8% is used.
        let meter = present(weekly(8), at(21, 11), nineToFive)
        #expect(meter.leftPercent == 92)
        #expect(meter.pace == .warn)
        #expect(meter.limitLabel?.hasPrefix("Out in ~") == true)
        #expect(present(weekly(20), at(21, 11), nineToFive).pace == .hot)
    }

    @Test func aSessionKeepsTheWallClock() {
        let session = UsageWindow(kind: "session", usedPercent: 50, resetsAt: at(21, 12), periodMs: 5 * 60 * 60 * 1000)
        #expect(present(session, at(21, 10)) == present(session, at(21, 10), nineToFive))
    }

    @Test func nextHourFollowsHalfHourZones() {
        let india = TimeZone(identifier: "Asia/Kolkata")!
        #expect(nextHour(at(21, 10, 40, zone: india), zone: india) == at(21, 11, 0, zone: india))
        #expect(abs(activeWeight(nineToFive, from: at(21, 8, 30), to: at(21, 9, 30), zone: prague) - 0.5) < 1e-9)
    }

    @Test func pacesAUsageWindow() {
        let hour: Int64 = 3_600_000
        let period = 5 * hour
        let now: Int64 = 1_000_000_000_000
        // Two hours in, 70% used: on course for 175%, out in under an hour.
        let behind = presentWindow(UsageWindow(kind: "session", usedPercent: 70, resetsAt: now + 3 * hour, periodMs: period), now: now)
        #expect(behind.pace == .hot)
        #expect(behind.leftPercent == 30)
        #expect(behind.limitLabel == "Out in ~52m")
        #expect(behind.resetLabel == "Resets in 3h")
        let ahead = presentWindow(UsageWindow(kind: "session", usedPercent: 10, resetsAt: now + 3 * hour, periodMs: period), now: now)
        #expect(ahead.pace == .ok)
        #expect(paceGap(ahead) == "30% ahead")
        #expect(presentWindow(UsageWindow(kind: "session", usedPercent: 0, resetsAt: nil, periodMs: period), now: now).resetLabel == "Not started")
        #expect(
            presentWindow(UsageWindow(kind: "weekly", usedPercent: 100, resetsAt: now + hour, periodMs: 7 * 24 * hour), now: now)
                .limitLabel == "Limit reached")
        #expect(creditsLabel(UsageCredits(balance: 62_500, unlimited: false)) == "62,500 credits")
        #expect(creditsReach(UsageCredits(balance: 1, unlimited: false, messages: [3, 8])) == "~3–8 messages")
    }

    @Test func callsAGuessedPaceAnEstimate() {
        let day: Int64 = 24 * 60 * 60 * 1000
        let now: Int64 = 1_000_000_000_000
        func monthly(_ used: Double, estimated: Bool?) -> UsageMeter {
            presentWindow(
                UsageWindow(kind: "monthly", usedPercent: used, resetsAt: now + 15 * day, periodMs: 30 * day, periodEstimated: estimated),
                now: now, zone: prague)
        }
        #expect(paceGap(monthly(45, estimated: true)) == "Est. 5% ahead")
        #expect(paceGap(monthly(53, estimated: true)) == "Est. 3% behind")
        #expect(paceGap(monthly(50, estimated: true)) == "Est. on pace")
        #expect(monthly(45, estimated: true).paceEstimated)
        // A known period keeps the plain wording.
        #expect(paceGap(monthly(45, estimated: nil)) == "5% ahead")
        #expect(!monthly(45, estimated: false).paceEstimated)
        // Nothing to compare once the limit is reached.
        #expect(paceGap(monthly(100, estimated: true)) == nil)
    }

    @Test func readsTheEstimateFlagFromTheHost() throws {
        let flagged = try JSONDecoder().decode(
            UsageWindow.self,
            from: Data(#"{"kind":"monthly","usedPercent":49,"resetsAt":1796000000000,"periodMs":2592000000,"periodEstimated":true}"#.utf8))
        #expect(flagged.periodEstimated == true)
        // A host from before the flag leaves it out.
        let plain = try JSONDecoder().decode(
            UsageWindow.self, from: Data(#"{"kind":"weekly","usedPercent":49,"resetsAt":null,"periodMs":604800000}"#.utf8))
        #expect(plain.periodEstimated == nil)
    }

    @Test func showsAnAskAnOlderHostRefusedAsFailedAndReadsOnOnceItAnswers() throws {
        let opencode = try JSONDecoder().decode(
            ProviderUsage.self,
            from: Data(#"{"provider":"opencode","windows":[{"kind":"weekly","usedPercent":40,"resetsAt":1800000000000,"periodMs":604800000}],"message":null}"#.utf8))
        var readings = UsageReadings()
        // An older Relay doesn't know OpenCode as an agent with limits and refuses the ask.
        readings.record("opencode", nil)
        #expect(readings.usage["opencode"] == nil)
        #expect(readings.failed == ["opencode"])
        // Asking again once the host answers replaces the failure.
        readings.record("opencode", opencode)
        #expect(readings.usage["opencode"] == opencode)
        #expect(readings.failed.isEmpty)
    }

    @Test func keepsTheLimitsReadBeforeAFailureAndAFailureToItsOwnAgent() throws {
        let claude = try JSONDecoder().decode(
            ProviderUsage.self, from: Data(#"{"provider":"claude","windows":[],"message":"No usage limits reported"}"#.utf8))
        var readings = UsageReadings()
        readings.record("claude", claude)
        readings.record("opencode", nil)
        #expect(readings.usage["claude"] == claude)
        #expect(readings.failed == ["opencode"])
        // A failed ask after a good one keeps the limits and says it failed.
        readings.record("claude", nil)
        #expect(readings.usage["claude"] == claude)
        #expect(readings.failed == ["claude", "opencode"])
        // Another connection's readings start empty.
        #expect(UsageReadings().failed.isEmpty)
    }

    @Test func aFailureReplacesAnEmptyStatusButKeepsMetersWhenAvailable() throws {
        let provider = "opencode"
        let ready = try JSONDecoder().decode(
            ProviderUsage.self,
            from: Data(#"{"provider":"opencode","windows":[{"kind":"weekly","usedPercent":40,"resetsAt":1800000000000,"periodMs":604800000}],"message":null}"#.utf8))
        let empty = try JSONDecoder().decode(
            ProviderUsage.self,
            from: Data(#"{"provider":"opencode","windows":[],"message":"Shows while OpenCode is running"}"#.utf8))
        var readings = UsageReadings()
        readings.record(provider, ready)
        #expect(!readings.unavailable(provider))
        readings.record(provider, empty)
        #expect(!readings.unavailable(provider))
        readings.record(provider, nil)
        #expect(readings.unavailable(provider))
        #expect(!readings.unavailable("claude"))
        readings.record(provider, ready)
        #expect(!readings.unavailable(provider))
        #expect(readings.usage[provider] == ready)
        readings.record(provider, nil)
        #expect(!readings.unavailable(provider))
        #expect(readings.usage[provider] == ready)
    }

    @Test func ignoresSupersededReadsWhenANewerAskHasStarted() throws {
        let stale = try JSONDecoder().decode(
            ProviderUsage.self,
            from: Data(#"{"provider":"opencode","windows":[{"kind":"weekly","usedPercent":80,"resetsAt":1800000000000,"periodMs":604800000}],"message":null}"#.utf8))
        let fresh = try JSONDecoder().decode(
            ProviderUsage.self,
            from: Data(#"{"provider":"opencode","windows":[{"kind":"weekly","usedPercent":20,"resetsAt":1800000000000,"periodMs":604800000}],"message":null}"#.utf8))
        let empty = try JSONDecoder().decode(
            ProviderUsage.self,
            from: Data(#"{"provider":"opencode","windows":[],"message":"Shows while OpenCode is running"}"#.utf8))
        var readings = UsageReadings()
        let first = readings.begin("opencode")
        let second = readings.begin("opencode")
        readings.record("opencode", fresh, token: second)
        readings.record("opencode", stale, token: first)
        readings.record("opencode", nil, token: first)
        #expect(readings.usage["opencode"] == fresh)
        #expect(readings.failed.isEmpty)
        // A late failure also cannot mark a newer empty reading as unavailable.
        let third = readings.begin("opencode")
        let fourth = readings.begin("opencode")
        readings.record("opencode", empty, token: fourth)
        readings.record("opencode", nil, token: third)
        #expect(!readings.unavailable("opencode"))
        #expect(readings.usage["opencode"] == empty)
    }
}
