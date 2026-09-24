import Darwin
import CryptoKit
import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings
import UserNotifications

extension DeviceActivityName {
    static let roomDNS = Self("roomdns.daily-focus")
}

extension ManagedSettingsStore.Name {
    static let roomDNS = Self("roomdns.focus")
}

enum PlaceMode: String, Codable {
    case gps
    case nfc // Decode-only compatibility: old rules remain editable, never active.
}

struct FocusRule: Codable, Identifiable {
    let id: UUID
    var name: String
    var selection: FamilyActivitySelection
    var targetCount: Int
    var startMinutes: Int
    var endMinutes: Int
    var placeMode: PlaceMode
    var hasPlace: Bool
    var placeLatitude: Double
    var placeLongitude: Double
    var nfcConfigured: Bool
    var preventAppRemoval: Bool? = nil // Missing in existing rules: opt-in stays off.
}

enum RestrictionPolicy {
    static func shouldShield(
        ruleEnabled: Bool,
        hasSelection: Bool,
        scheduleActive: Bool,
        placeMode: PlaceMode,
        insidePlace: Bool,
        placeFocusConfirmed: Bool,
        safetyReleased: Bool
    ) -> Bool {
        ruleEnabled
            && hasSelection
            && scheduleActive
            && placeMode == .gps && insidePlace && placeFocusConfirmed
            && !safetyReleased
    }

    // A trustworthy fix can keep the previous state in the boundary band, but never invent entry.
    static func placePresence(distance: Double, horizontalAccuracy: Double,
                              sampleDate: Date, now: Date, wasInside: Bool) -> Bool? {
        let age = now.timeIntervalSince(sampleDate)
        guard distance.isFinite, distance >= 0, horizontalAccuracy.isFinite,
              (0...100).contains(horizontalAccuracy), (0...30).contains(age) else { return nil }
        if distance + horizontalAccuracy <= 150 { return true }
        if distance - horizontalAccuracy > 200 { return false }
        return wasInside
    }

    static func isScheduleActive(
        at date: Date,
        startMinutes: Int,
        endMinutes: Int,
        calendar: Calendar = .current
    ) -> Bool {
        let components = calendar.dateComponents([.hour, .minute], from: date)
        let current = (components.hour ?? 0) * 60 + (components.minute ?? 0)

        guard startMinutes != endMinutes else { return false }
        if startMinutes < endMinutes {
            return current >= startMinutes && current < endMinutes
        }
        return current >= startMinutes || current < endMinutes
    }

    static func intervalMinutes(start: Int, end: Int) -> Int {
        (end - start + 1_440) % 1_440
    }

    static func nextScheduleEnd(
        after date: Date,
        endMinutes: Int,
        calendar: Calendar = .current
    ) -> Date? {
        calendar.nextDate(
            after: date,
            matching: DateComponents(
                hour: endMinutes / 60,
                minute: endMinutes % 60,
                second: 0
            ),
            matchingPolicy: .nextTimePreservingSmallerComponents
        )
    }

    static func scheduleOccurrenceStart(
        at date: Date,
        startMinutes: Int,
        endMinutes: Int,
        calendar: Calendar = .current
    ) -> Date? {
        guard isScheduleActive(at: date, startMinutes: startMinutes, endMinutes: endMinutes, calendar: calendar) else { return nil }
        let minute = calendar.component(.hour, from: date) * 60 + calendar.component(.minute, from: date)
        var day = calendar.startOfDay(for: date)
        if startMinutes > endMinutes, minute < endMinutes {
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day) else { return nil }
            day = previous
        }
        return calendar.date(byAdding: .minute, value: startMinutes, to: day)
    }

    static func unlockSessionID(generation: UUID, occurrenceStart: Date) -> String {
        let value = "v1|\(generation.uuidString.lowercased())|\(Int64(occurrenceStart.timeIntervalSince1970))"
        return SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

// Persisted with the runtime lock so the app and monitor extension share one event checkpoint.
struct RestrictionNotificationSnapshot: Codable, Equatable {
    var ruleID: UUID?
    var ruleName: String
    var generation: UUID
    var insidePlace: Bool?
    var shielding: Bool

    func events(since previous: Self?) -> [RestrictionNotificationEvent] {
        var events: [RestrictionNotificationEvent] = []
        let sameRule = previous?.ruleID == ruleID
        let sameObservation = sameRule && previous?.generation == generation
        if ruleID != nil, let insidePlace {
            if insidePlace && (!sameObservation || previous?.insidePlace != true) {
                events.append(.entered(ruleName))
            } else if !insidePlace && sameObservation && previous?.insidePlace == true {
                events.append(.left(ruleName))
            }
        }
        if let previous, previous.shielding && (!shielding || !sameRule) {
            events.append(.ended(previous.ruleName))
        }
        if shielding && (previous?.shielding != true || !sameRule) {
            events.append(.started(ruleName))
        }
        return events
    }
}

enum RestrictionNotificationEvent: Equatable {
    case entered(String), left(String), started(String), ended(String)

    var title: String {
        switch self {
        case .entered: return "Zone entered"
        case .left: return "Zone left"
        case .started: return "Blocking started"
        case .ended: return "Blocking ended"
        }
    }

    var body: String {
        let key: String
        let name: String
        switch self {
        case .entered(let value): key = "You are inside %@."; name = value
        case .left(let value): key = "You are outside %@."; name = value
        case .started(let value): key = "%@: your selected apps and websites are now blocked."; name = value
        case .ended(let value): key = "%@: this rule is no longer blocking apps or websites."; name = value
        }
        return String(format: Bundle.main.localizedString(forKey: key, value: key, table: nil), name)
    }
}

final class RestrictionNotifications: NSObject, UNUserNotificationCenterDelegate {
    static let shared = RestrictionNotifications()
    static let enabledKey = "zoneNotificationsEnabled"

    static func send(_ events: [RestrictionNotificationEvent]) {
        guard SharedState.defaults.bool(forKey: enabledKey) else { return }
        for event in events {
            let content = UNMutableNotificationContent()
            content.title = Bundle.main.localizedString(forKey: event.title, value: event.title, table: nil)
            content.body = event.body
            content.sound = .default
            // No app tokens, selected app names, or coordinates enter notification content.
            let request = UNNotificationRequest(identifier: "roomdns.event." + UUID().uuidString,
                                                content: content, trigger: nil)
            UNUserNotificationCenter.current().add(request) { error in
                if error != nil { NSLog("Offzone could not enqueue a status notification.") }
            }
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }
}

// A single applied configuration: saving an editable rule never changes this snapshot.
struct RuntimeState: Codable {
    var schemaVersion = 1
    var rule: FocusRule?
    var enabled = false
    var generation = UUID()
    var insidePlace = false
    var nfcActive = false
    var safetyReleaseUntil: Date?
    var recoveryPaused = false
    var confirmedInsidePlace: Bool?
    var notificationSnapshot: RestrictionNotificationSnapshot?
    var placeObservedAt: Date?
    var placeFocusOccurrenceStart: Date?

    func placeFocusConfirmed(at date: Date, calendar: Calendar = .current) -> Bool {
        guard let rule, let placeFocusOccurrenceStart else { return false }
        return placeFocusOccurrenceStart == RestrictionPolicy.scheduleOccurrenceStart(
            at: date, startMinutes: rule.startMinutes, endMinutes: rule.endMinutes, calendar: calendar)
    }

    func canStartPlaceFocus(at date: Date, calendar: Calendar = .current) -> Bool {
        guard enabled, !recoveryPaused, let rule, rule.placeMode == .gps, rule.hasPlace,
              insidePlace, confirmedInsidePlace == true, let placeObservedAt,
              (0...30).contains(date.timeIntervalSince(placeObservedAt)) else { return false }
        return RestrictionPolicy.isScheduleActive(at: date, startMinutes: rule.startMinutes,
                                                  endMinutes: rule.endMinutes, calendar: calendar)
    }

    func shouldShield(at date: Date) -> Bool {
        guard schemaVersion == 1, let rule else { return false }
        return RestrictionPolicy.shouldShield(
            ruleEnabled: enabled && !recoveryPaused,
            hasSelection: !rule.selection.applicationTokens.isEmpty
                || !rule.selection.categoryTokens.isEmpty
                || !rule.selection.webDomainTokens.isEmpty,
            scheduleActive: RestrictionPolicy.isScheduleActive(
                at: date, startMinutes: rule.startMinutes, endMinutes: rule.endMinutes),
            placeMode: rule.placeMode, insidePlace: insidePlace, placeFocusConfirmed: placeFocusConfirmed(at: date),
            safetyReleased: safetyReleaseUntil.map { $0 > date } ?? false
        )
    }
}

enum SharedState {
    static let appGroupIdentifier = "group.com.exchip.roomdns"
    static let defaults = UserDefaults(suiteName: appGroupIdentifier)!

    enum StorageError: LocalizedError {
        case unavailable, invalidRules, invalidRuntime, placeNotReady
        var errorDescription: String? {
            let key: String
            switch self {
            case .placeNotReady: key = "Confirm your location inside the saved place during its schedule, then try again."
            case .unavailable: key = "Shared storage is unavailable. Your rules have not been replaced."
            case .invalidRules: key = "Saved rules could not be read. The original data has been kept."
            case .invalidRuntime: key = "The active rule could not be read. The original data has been kept."
            }
            return Bundle.main.localizedString(forKey: key, value: key, table: nil)
        }
    }

    static var runtimeURL: URL? {
#if targetEnvironment(simulator)
        // Unsigned simulator builds have no App Group entitlement. This is a UI/policy sandbox only.
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("RoomDNSSimulator", isDirectory: true)
        do { try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true) }
        catch { return nil }
        return directory.appendingPathComponent("runtime-v1.json")
#else
        return FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier)?
            .appendingPathComponent("runtime-v1.json")
#endif
    }

    // ponytail: one short cross-process lock for restriction state; keep growing history out of it.
    private static func accessRuntime<T>(write: Bool = false, _ body: (inout RuntimeState) throws -> T) throws -> T {
        guard let url = runtimeURL else { throw StorageError.unavailable }
        let lockURL = url.deletingLastPathComponent().appendingPathComponent("runtime.lock")
        let lockExists = FileManager.default.fileExists(atPath: lockURL.path)
        let descriptor = open(lockURL.path, O_CREAT | O_RDWR, S_IRUSR | S_IWUSR)
        guard descriptor >= 0 else { throw StorageError.unavailable }
        defer { close(descriptor) }
        if !lockExists {
            try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: lockURL.path)
        }
        var locked = false
        for _ in 0..<5 {
            if flock(descriptor, LOCK_EX | LOCK_NB) == 0 { locked = true; break }
            guard errno == EWOULDBLOCK || errno == EINTR else { throw StorageError.unavailable }
            usleep(2_000)
        }
        guard locked else { throw StorageError.unavailable }
        defer { flock(descriptor, LOCK_UN) }
        let exists = FileManager.default.fileExists(atPath: url.path)
        var state: RuntimeState
        if exists {
            do {
                state = try JSONDecoder().decode(RuntimeState.self, from: Data(contentsOf: url))
                guard state.schemaVersion == 1 else { throw StorageError.invalidRuntime }
            } catch { throw StorageError.invalidRuntime }
        } else {
            state = try legacyRuntime()
        }
        // Keep legacy NFC rule data for editing, but never resume its old runtime session.
        if state.rule?.placeMode == .nfc {
            state.enabled = false
            state.nfcActive = false
            state.placeFocusOccurrenceStart = nil
        }
        let previousNotification = state.notificationSnapshot
        let appliedAt = Date.now
        let result = try body(&state)
        var events: [RestrictionNotificationEvent] = []
        if write {
            let snapshot = RestrictionNotificationSnapshot(
                ruleID: state.rule?.id, ruleName: state.rule?.name ?? "",
                generation: state.generation,
                insidePlace: state.enabled && !state.recoveryPaused && state.rule?.placeMode == .gps
                    ? state.confirmedInsidePlace : nil,
                shielding: state.shouldShield(at: appliedAt))
            events = snapshot.events(since: previousNotification)
            state.notificationSnapshot = snapshot
        }
        if write || !exists {
            try JSONEncoder().encode(state).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        }
        if write {
            apply(state, at: appliedAt)
            // Best effort, after persistence and policy application. Delivery never gates blocking.
            RestrictionNotifications.send(events)
        }
        return result
    }

    static func readRuntime() throws -> RuntimeState { try accessRuntime { $0 } }
    static var runtime: RuntimeState {
        (try? readRuntime()) ?? RuntimeState(recoveryPaused: true)
    }

    static func readRules() throws -> [FocusRule] {
        guard let original = defaults.object(forKey: "rules") else { return [] }
        guard let data = original as? Data else { throw StorageError.invalidRules }
        guard let value = try? JSONDecoder().decode([FocusRule].self, from: data),
              Set(value.map(\.id)).count == value.count,
              value.allSatisfy({ (0..<1440).contains($0.startMinutes)
                  && (0..<1440).contains($0.endMinutes)
                  && RestrictionPolicy.intervalMinutes(start: $0.startMinutes, end: $0.endMinutes) >= 15 })
        else { throw StorageError.invalidRules }
        return value
    }

    static func saveRules(_ rules: [FocusRule]) throws {
        _ = try readRules() // Never overwrite undecodable original data with an empty collection.
        let data = try JSONEncoder().encode(rules)
        if defaults.data(forKey: "rules.originalBackup") == nil,
           let original = defaults.data(forKey: "rules") {
            defaults.set(original, forKey: "rules.originalBackup")
        }
        defaults.set(data, forKey: "rules")
    }

    static var rules: [FocusRule] { (try? readRules()) ?? [] }
    static var rulesStorageError: Bool { (try? readRules()) == nil }

    private static func legacyRuntime() throws -> RuntimeState {
        var saved = try readRules()
        guard defaults.bool(forKey: "ruleEnabled") else { return RuntimeState() }
        let selection = defaults.data(forKey: "selection")
            .flatMap { try? JSONDecoder().decode(FamilyActivitySelection.self, from: $0) }
        guard let selection else { return RuntimeState(recoveryPaused: true) }
        let rawID = defaults.string(forKey: "activeRuleID")
        let oldID = rawID.flatMap(UUID.init(uuidString:))
        if rawID != nil && !saved.contains(where: { $0.id == oldID }) {
            return RuntimeState(recoveryPaused: true)
        }
        let selected = saved.first { $0.id == oldID } ?? saved.first
        let hasPlace = defaults.bool(forKey: "hasPlace")
        let mode = defaults.string(forKey: "placeMode").flatMap(PlaceMode.init(rawValue:))
            ?? (hasPlace ? .gps : .nfc)
        let rule = FocusRule(
            id: selected?.id ?? UUID(), name: selected?.name ?? "My time",
            selection: selection,
            targetCount: selection.applicationTokens.count + selection.categoryTokens.count + selection.webDomainTokens.count,
            startMinutes: defaults.object(forKey: "scheduleStart") as? Int ?? 540,
            endMinutes: defaults.object(forKey: "scheduleEnd") as? Int ?? 1080,
            placeMode: mode, hasPlace: hasPlace,
            placeLatitude: defaults.double(forKey: "placeLatitude"),
            placeLongitude: defaults.double(forKey: "placeLongitude"),
            nfcConfigured: defaults.object(forKey: "nfcConfigured") as? Bool ?? (mode == .nfc))
        guard (0..<1440).contains(rule.startMinutes), (0..<1440).contains(rule.endMinutes),
              RestrictionPolicy.intervalMinutes(start: rule.startMinutes, end: rule.endMinutes) >= 15
        else { throw StorageError.invalidRuntime }
        // Only the pre-collection format is migrated. An explicit empty collection stays empty.
        if defaults.object(forKey: "rules") == nil {
            saved = [rule]
            try saveRules(saved)
        }
        guard saved.contains(where: { $0.id == rule.id }) else { return RuntimeState(recoveryPaused: true) }
        return RuntimeState(rule: rule, enabled: mode == .gps, insidePlace: false,
                            nfcActive: false,
                            safetyReleaseUntil: defaults.object(forKey: "safetyReleaseUntil") as? Date)
    }

    static var activeRuleID: UUID? { runtime.rule?.id }
    static var appliedRule: FocusRule? { runtime.rule }
    static var ruleEnabled: Bool { let state = runtime; return state.enabled && !state.recoveryPaused }
    static var selection: FamilyActivitySelection { runtime.rule?.selection ?? FamilyActivitySelection() }
    static var scheduleStartMinutes: Int { runtime.rule?.startMinutes ?? 540 }
    static var scheduleEndMinutes: Int { runtime.rule?.endMinutes ?? 1080 }
    static var placeMode: PlaceMode { runtime.rule?.placeMode ?? .gps }
    static var hasPlace: Bool { runtime.rule?.hasPlace ?? false }
    static var placeLatitude: Double { runtime.rule?.placeLatitude ?? 0 }
    static var placeLongitude: Double { runtime.rule?.placeLongitude ?? 0 }
    static var insidePlace: Bool { runtime.insidePlace }
    static var safetyReleaseUntil: Date? { runtime.safetyReleaseUntil }
    static var safetyReleased: Bool {
        let state = runtime
        return state.recoveryPaused || (state.safetyReleaseUntil.map { $0 > Date.now } ?? false)
    }
    static var scheduleActive: Bool {
        guard let rule = runtime.rule else { return false }
        return RestrictionPolicy.isScheduleActive(at: .now, startMinutes: rule.startMinutes, endMinutes: rule.endMinutes)
    }
    static var shouldShield: Bool { runtime.shouldShield(at: .now) }
    static var placeIdentifier: String { "place-" + runtime.generation.uuidString }

    static func activate(_ rule: FocusRule) throws {
        try accessRuntime(write: true) { state in
            guard rule.placeMode == .gps, rule.hasPlace else { throw StorageError.placeNotReady }
            state = RuntimeState(rule: rule, enabled: true)
        }
    }

    static func deactivate() throws {
        try accessRuntime(write: true) { state in
            state = RuntimeState(recoveryPaused: true)
        }
    }

    static func restoreAccess(pause: Bool, at now: Date = .now) throws {
        try accessRuntime(write: true) { state in
            state.generation = UUID() // Invalidates callbacks belonging to the previous intent.
            state.nfcActive = false
            state.insidePlace = false
            state.confirmedInsidePlace = nil
            state.placeObservedAt = nil
            state.placeFocusOccurrenceStart = nil
            if pause || state.rule == nil {
                state.recoveryPaused = true
                state.enabled = false
                state.safetyReleaseUntil = nil
            } else if let rule = state.rule {
                state.safetyReleaseUntil = RestrictionPolicy.nextScheduleEnd(after: now, endMinutes: rule.endMinutes)
            }
        }
    }

    static func restoreAccess(usingUnlockPass sessionID: String, at now: Date = .now,
                              calendar: Calendar = .current) throws -> Bool {
        try accessRuntime(write: true) { state in
            guard state.enabled, !state.recoveryPaused, let rule = state.rule,
                  state.safetyReleaseUntil.map({ $0 <= now }) ?? true,
                  rule.placeMode == .gps, state.insidePlace, state.placeFocusConfirmed(at: now, calendar: calendar),
                  let occurrence = RestrictionPolicy.scheduleOccurrenceStart(
                    at: now, startMinutes: rule.startMinutes, endMinutes: rule.endMinutes, calendar: calendar),
                  RestrictionPolicy.unlockSessionID(generation: state.generation, occurrenceStart: occurrence) == sessionID
            else { return false }
            state.generation = UUID()
            state.nfcActive = false
            state.insidePlace = false
            state.confirmedInsidePlace = nil
            state.placeObservedAt = nil
            state.placeFocusOccurrenceStart = nil
            state.safetyReleaseUntil = RestrictionPolicy.nextScheduleEnd(
                after: now, endMinutes: rule.endMinutes, calendar: calendar)
            return true
        }
    }

    static func setInsidePlace(_ inside: Bool, generation: UUID? = nil, confirmed: Bool = false,
                               observedAt: Date = .now) {
        do {
            try accessRuntime(write: true) { state in
                if let generation, state.generation != generation { return }
                guard state.rule?.placeMode == .gps, state.enabled, !state.recoveryPaused else { return }
                state.insidePlace = inside
                if confirmed {
                    state.confirmedInsidePlace = inside
                    state.placeObservedAt = observedAt
                }
                if !inside {
                    state.placeFocusOccurrenceStart = nil
                    state.placeObservedAt = nil
                }
            }
        } catch { clearRestrictions() }
    }

    static func startPlaceFocus(generation: UUID, at now: Date = .now,
                                calendar: Calendar = .current) throws {
        try accessRuntime(write: true) { state in
            guard state.generation == generation, state.canStartPlaceFocus(at: now, calendar: calendar),
                  let rule = state.rule else { throw StorageError.placeNotReady }
            state.safetyReleaseUntil = nil // Only a fresh, explicit Start focus can resume after recovery.
            state.placeFocusOccurrenceStart = RestrictionPolicy.scheduleOccurrenceStart(
                at: now, startMinutes: rule.startMinutes, endMinutes: rule.endMinutes, calendar: calendar)
        }
    }

    static func reconcileRestrictions(locationAllowed: Bool? = nil) {
        do {
            try accessRuntime(write: true) { state in
                let now = Date.now
                if locationAllowed == false {
                    state.insidePlace = false
                    state.confirmedInsidePlace = nil
                    state.placeObservedAt = nil
                    state.placeFocusOccurrenceStart = nil
                }
                if let rule = state.rule,
                   !RestrictionPolicy.isScheduleActive(at: now, startMinutes: rule.startMinutes, endMinutes: rule.endMinutes) {
                    state.placeFocusOccurrenceStart = nil
                }
            }
        } catch { clearRestrictions() }
    }

    static func clearRestrictions() { ManagedSettingsStore(named: .roomDNS).clearAllSettings() }

    private static func apply(_ state: RuntimeState, at now: Date) {
        let store = ManagedSettingsStore(named: .roomDNS)
        guard state.shouldShield(at: now), let rule = state.rule else {
            store.clearAllSettings()
            return
        }
        // Screen Time authorization can be revoked by the device owner. This is not a device lock.
        store.application.denyAppRemoval = rule.preventAppRemoval == true ? true : nil
        let value = rule.selection
        store.shield.applications = value.applicationTokens.isEmpty ? nil : value.applicationTokens
        store.shield.applicationCategories = value.categoryTokens.isEmpty ? nil : .specific(value.categoryTokens)
        store.shield.webDomains = value.webDomainTokens.isEmpty ? nil : value.webDomainTokens
        store.shield.webDomainCategories = value.categoryTokens.isEmpty ? nil : .specific(value.categoryTokens)
    }
}
