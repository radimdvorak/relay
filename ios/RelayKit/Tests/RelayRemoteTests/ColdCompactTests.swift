import Foundation
import Testing

@testable import RelayRemote

// Mirrors Android's ColdCompactTest.
struct ColdCompactTests {
    let hour: Int64 = 60 * 60_000
    let claude = ResumeSettings(provider: "claude", choice: ModelChoice(model: ""), runtimeMode: "auto", interactionMode: "default")

    func answer(_ tokens: Int64, provider: String = "claude") -> ChatMessage {
        let json = #"{"id":"a\#(tokens)","role":"assistant","body":"Done","status":"complete","created":0,"provider":"\#(provider)","version":0,"context":{"usedTokens":\#(tokens),"cache":{"at":0,"ttlMs":\#(hour)}}}"#
        return try! JSON.parse(json).decode()
    }

    func send(_ body: String = "Carry on", provider: String = "claude", delivery: String? = nil) -> ProjectChatSend {
        var settings = claude
        settings.provider = provider
        return ProjectChatSend(id: "s1", body: body, settings: settings, delivery: delivery)
    }

    func tokens(_ messages: [ChatMessage], _ body: String = "Carry on", queueWaiting: Bool = false, now: Int64? = nil) -> Int64? {
        coldCompactTokens(
            messages, holder: contextAgent(messages), send: send(body), queueWaiting: queueWaiting, now: now ?? 2 * hour)
    }

    @Test func aBigConversationWhoseCacheWentColdAsks() {
        #expect(tokens([answer(214_000)]) == 214_000)
    }

    @Test func aWarmCacheOrASmallConversationGoesAsIs() {
        #expect(tokens([answer(214_000)], now: hour / 2) == nil)
        #expect(tokens([answer(90_000)]) == nil)
    }

    @Test func anotherAgentAQueueOrACommandGoesAsIs() {
        let messages = [answer(214_000)]
        #expect(coldCompactTokens(messages, holder: "claude", send: send(provider: "codex"), queueWaiting: false, now: 2 * hour) == nil)
        #expect(tokens(messages, queueWaiting: true) == nil)
        #expect(tokens(messages, "@claude /goal ship it") == nil)
        #expect(coldCompactTokens(messages, holder: "claude", send: send(delivery: "queue"), queueWaiting: false, now: 2 * hour) == nil)
    }
}
