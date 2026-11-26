// Plays a pattern on the trackpad's haptic motor, through macOS's private
// MultitouchSupport framework; Apple's public haptics only play while a
// finger is on the trackpad. Looked up at run time, so a macOS without these
// functions makes this exit 1 instead of failing to launch.
//
//   trackpad-taps --check
//   trackpad-taps <strength> <hz:ms:level:wait>...
import Foundation

typealias CreateList = @convention(c) () -> Unmanaged<CFArray>?
typealias GetID = @convention(c) (UnsafeMutableRawPointer, UnsafeMutablePointer<UInt64>) -> Int32
typealias CreateActuator = @convention(c) (UInt64) -> Unmanaged<CFTypeRef>?
typealias OpenActuator = @convention(c) (CFTypeRef) -> Int32
typealias CreateActuation = @convention(c) (CFDictionary, Int32) -> UnsafeMutableRawPointer?
typealias Actuate = @convention(c) (UnsafeMutableRawPointer, UnsafeMutableRawPointer, UInt32, Float, Float) -> Int32

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data((message + "\n").utf8))
    exit(1)
}

let framework = dlopen(
    "/System/Library/PrivateFrameworks/MultitouchSupport.framework/MultitouchSupport", RTLD_NOW)

func symbol<T>(_ name: String, as type: T.Type) -> T {
    guard let framework, let address = dlsym(framework, name) else { fail("no \(name)") }
    return unsafeBitCast(address, to: type)
}

let createList = symbol("MTDeviceCreateList", as: CreateList.self)
let getID = symbol("MTDeviceGetDeviceID", as: GetID.self)
let createActuator = symbol("MTActuatorCreateFromDeviceID", as: CreateActuator.self)
let openActuator = symbol("MTActuatorOpen", as: OpenActuator.self)
let createActuation = symbol("MTActuationCreateFromDictionary", as: CreateActuation.self)
let actuate = symbol("MTActuationActuate", as: Actuate.self)

// Held for the whole run: the devices go with the list.
let devices = createList()?.takeRetainedValue()

func openMotors() -> [CFTypeRef] {
    guard let devices else { return [] }
    return (0..<CFArrayGetCount(devices)).compactMap { index in
        guard let device = CFArrayGetValueAtIndex(devices, index) else { return nil }
        var id: UInt64 = 0
        guard getID(UnsafeMutableRawPointer(mutating: device), &id) == 0,
            let motor = createActuator(id)?.takeRetainedValue(),
            openActuator(motor) == 0
        else { return nil }
        return motor
    }
}

struct Step {
    let hz: Double
    let ms: Double
    let level: Float
    let wait: Double

    init?(_ text: String) {
        let parts = text.split(separator: ":").compactMap { Double($0) }
        guard parts.count == 4, parts.allSatisfy({ $0.isFinite && $0 >= 0 }) else { return nil }
        hz = parts[0]
        ms = parts[1]
        level = Float(min(1, parts[2]))
        wait = min(5000, parts[3])
    }
}

func play(_ step: Step, strength: Float) {
    let shape: [String: Any] = [
        "BaseWaveform": ["Type": "Gaussian", "Amplitude": 0.0, "DurationMS": step.ms],
        "Tones": [
            [
                "Type": "Sine", "Amplitude": 1.0, "DurationMS": step.ms, "DelayMS": 0.0,
                "FrequencykHz": step.hz / 1000,
            ]
        ],
    ]
    guard let actuation = createActuation(shape as CFDictionary, 0) else { fail("no actuation") }
    for motor in motors {
        let pointer = Unmanaged.passUnretained(motor as AnyObject).toOpaque()
        _ = actuate(actuation, pointer, 0, step.level * strength, 0)
    }
    Unmanaged<AnyObject>.fromOpaque(actuation).release()
}

let motors = openMotors()
if motors.isEmpty { fail("no trackpad with a haptic motor") }

let arguments = Array(CommandLine.arguments.dropFirst())
if arguments == ["--check"] { exit(0) }

guard let first = arguments.first, let wanted = Float(first), wanted.isFinite else {
    fail("no strength")
}
// Past 1 is louder, but the driver starts to stall.
let strength = min(1, max(0, wanted))
let steps = arguments.dropFirst().map { text -> Step in
    guard let step = Step(text) else { fail("not a step: \(text)") }
    return step
}

var last = Date()
for step in steps {
    play(step, strength: strength)
    // From when this one was due, so a slow call doesn't drag the rhythm.
    last += step.wait / 1000
    Thread.sleep(until: last)
}
// Exiting closes the motor, which would cut the last vibration short.
Thread.sleep(forTimeInterval: (steps.last?.ms ?? 0) / 1000 + 0.1)
