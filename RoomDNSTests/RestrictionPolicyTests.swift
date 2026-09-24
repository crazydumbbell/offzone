import Darwin
import CoreLocation
import XCTest
import UIKit
import SwiftUI
import RevenueCat
@testable import RoomDNS

final class RestrictionPolicyTests: XCTestCase {
    @MainActor
    func testRueAssetsAndStateMapping() throws {
        XCTAssertEqual(OffzoneRueExpression.allCases.count, 8)
        XCTAssertEqual(OffzoneRueFullBody.allCases.count, 4)
        for expression in OffzoneRueExpression.allCases {
            XCTAssertNotNil(UIImage(named: expression.assetName), expression.assetName)
        }
        for expression in OffzoneRueFullBody.allCases {
            XCTAssertNotNil(UIImage(named: expression.assetName), expression.assetName)
        }
        let mapping: [(RoomSpiritState, OffzoneRueExpression)] = [
            (.welcome, .welcome), (.idle, .ready), (.attentive, .ready),
            (.guiding, .welcome), (.working, .reflection), (.confirmed, .ready),
            (.focused, .focused), (.needsAction, .needsAction), (.failed, .failed),
            (.celebrating, .ready), (.recovered, .recovered), (.returning, .welcome),
            (.finished, .finished)
        ]
        XCTAssertEqual(mapping.count, 13)
        for (state, portrait) in mapping {
            XCTAssertEqual(OffzoneRueExpression.expression(for: state), portrait)
        }
        XCTAssertEqual(OffzoneRueFullBody.expression(for: .welcome), .welcome)
        XCTAssertNil(OffzoneRueFullBody.expression(for: .failed))
        XCTAssertEqual(RoomSpiritMotion.sample(at: 0.7).breath, 1, accuracy: 0.001)
        XCTAssertEqual(RoomSpiritReaction.sample(state: .welcome, elapsed: 0.9), .init())
    }

    @MainActor
    func testSUITBundledFacesAndReadableSizes() {
        for weight: Font.Weight in [.regular, .medium, .semibold, .bold] {
            let font = UIFont(name: Font.suitFace(weight), size: 16)
            XCTAssertNotNil(font)
            XCTAssertTrue(font?.familyName.contains("SUIT") == true)
        }
        for style: Font.TextStyle in [.caption2, .caption, .footnote, .subheadline, .body, .headline, .title, .largeTitle] {
            XCTAssertGreaterThanOrEqual(Font.suitSize(style), 14)
        }
    }

    func testOnboardingMigrationAndSuggestedTimes() {
        XCTAssertTrue(OnboardingProfile.shouldShowWelcome(hasRules: false, storageError: false, completed: false))
        XCTAssertFalse(OnboardingProfile.shouldShowWelcome(hasRules: true, storageError: false, completed: false))
        XCTAssertFalse(OnboardingProfile.shouldShowWelcome(hasRules: false, storageError: false, completed: true))
        XCTAssertFalse(OnboardingProfile.shouldShowWelcome(hasRules: false, storageError: true, completed: false))
        XCTAssertEqual(FocusGoal.rest.suggestedWindow, .evening)
        XCTAssertEqual(FocusGoal.work.suggestedWindow, .morning)
        XCTAssertNil(FocusGoal(rawValue: "invalid"))
        for window in FocusWindow.allCases {
            XCTAssertEqual(RestrictionPolicy.intervalMinutes(start: window.startMinutes, end: window.endMinutes), 60)
            XCTAssertEqual(Calendar.current.component(.hour, from: window.date(minutes: window.startMinutes)), window.startMinutes / 60)
        }
        XCTAssertFalse(BillingTerms.duration(SubscriptionPeriod(value: 1, unit: .week)).isEmpty)
        XCTAssertNotEqual(BillingTerms.duration(SubscriptionPeriod(value: 1, unit: .month)),
                          BillingTerms.duration(SubscriptionPeriod(value: 1, unit: .month), count: 3))
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let start = calendar.date(from: DateComponents(year: 2026, month: 1, day: 31))!
        let end = BillingTerms.endDate(SubscriptionPeriod(value: 1, unit: .month), starting: start, calendar: calendar)!
        XCTAssertEqual(calendar.component(.month, from: end), 2)
        XCTAssertEqual(calendar.component(.day, from: end), 28)
    }

    private var originalDefaults: [String: Any] = [:]
    private var originalRuntime: Data?

    override func setUpWithError() throws {
        originalDefaults = SharedState.defaults.persistentDomain(forName: SharedState.appGroupIdentifier) ?? [:]
        if let url = SharedState.runtimeURL { originalRuntime = try? Data(contentsOf: url) }
        SharedState.defaults.removePersistentDomain(forName: SharedState.appGroupIdentifier)
        if let url = SharedState.runtimeURL, FileManager.default.fileExists(atPath: url.path) {
            try FileManager.default.removeItem(at: url)
        }
    }

    override func tearDownWithError() throws {
        SharedState.clearRestrictions()
        SharedState.defaults.setPersistentDomain(originalDefaults, forName: SharedState.appGroupIdentifier)
        if let url = SharedState.runtimeURL {
            if let originalRuntime { try originalRuntime.write(to: url, options: .atomic) }
            else if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
        }
    }

    private func sampleRule(mode: PlaceMode = .gps) -> FocusRule {
        FocusRule(id: UUID(), name: "Evening", selection: .init(), targetCount: 1,
                  startMinutes: 0, endMinutes: 1439, placeMode: mode,
                  hasPlace: mode == .gps, placeLatitude: 37.5, placeLongitude: 127,
                  nfcConfigured: mode == .nfc)
    }

#if targetEnvironment(simulator)
    @MainActor
    func testDeletionProtectionOptInSurvivesEditingWithoutChangingActiveRule() async throws {
        let legacy = sampleRule()
        var encoded = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(legacy)) as? [String: Any])
        encoded.removeValue(forKey: "preventAppRemoval")
        let decoded = try JSONDecoder().decode(FocusRule.self, from: JSONSerialization.data(withJSONObject: encoded))
        XCTAssertNil(decoded.preventAppRemoval)
        try SharedState.saveRules([decoded])
        let model = AppModel()
        model.loadRuleForEditing(decoded.id)
        XCTAssertFalse(model.preventAppRemoval)
        XCTAssertTrue(model.activateRule(decoded.id))
        model.preventAppRemoval = true
        XCTAssertTrue(model.saveRule(placeMode: .gps))
        XCTAssertTrue(model.hasUnappliedChanges(decoded.id))
        XCTAssertNotEqual(SharedState.appliedRule?.preventAppRemoval, true)
        XCTAssertTrue(model.activateRule(decoded.id))
        XCTAssertEqual(SharedState.appliedRule?.preventAppRemoval, true)
        model.beginNewRuleDraft()
        XCTAssertFalse(model.preventAppRemoval)
        model.loadRuleForEditing(decoded.id)
        XCTAssertTrue(model.preventAppRemoval)
        model.preventAppRemoval = false
        XCTAssertTrue(model.saveRule(placeMode: .gps))
        XCTAssertEqual(SharedState.appliedRule?.preventAppRemoval, true)
        XCTAssertTrue(model.hasUnappliedChanges(decoded.id))
        XCTAssertTrue(model.activateRule(decoded.id))
        XCTAssertEqual(SharedState.appliedRule?.preventAppRemoval, false)
        model.safetyRelease()
        XCTAssertFalse(SharedState.shouldShield)
        XCTAssertTrue(model.deleteRule(decoded.id))
        XCTAssertNil(SharedState.appliedRule)
    }

    @MainActor
    func testFeedbackShowsErrorsWithoutNormalStatusMessages() async throws {
        let model = AppModel()
        XCTAssertNil(model.errorMessage)
        XCTAssertFalse(model.saveRule(placeMode: .gps))
        XCTAssertEqual(model.errorMessage, model.message)
        model.requestTargetSelection()
        XCTAssertNil(model.errorMessage)
        model.selectPlace(.init(latitude: 37.5, longitude: 127))
        XCTAssertNil(model.errorMessage)
        SharedState.defaults.set("invalid rules", forKey: "rules")
        model.refresh()
        XCTAssertNotNil(model.errorMessage)
    }

    @MainActor
    func testCancelledLocationLookupLeavesNewDraftUntouched() async {
        let model = AppModel()
        model.requestCurrentPlace()
        model.beginNewRuleDraft()
        model.locationManager(CLLocationManager(), didUpdateLocations: [CLLocation(latitude: 37.5, longitude: 127)])
        let message = model.message
        model.locationManager(CLLocationManager(), didFailWithError: CLError(.locationUnknown))
        // Give delegate deliveries their MainActor turn after the draft was replaced.
        await Task.yield()
        XCTAssertFalse(model.draftHasPlace)
        XCTAssertNil(model.draftPlaceCoordinate)
        XCTAssertEqual(model.message, message)
    }

    @MainActor
    func testSaveDoesNotActivateOrOverwriteAppliedRuleAndRecovery() async throws {
        let model = AppModel()
        let granted = await model.requestScreenTimeAuthorization()
        XCTAssertTrue(granted)
        model.requestTargetSelection()
        model.selectPlace(.init(latitude: 37.5665, longitude: 126.9780))
        model.startTime = .now
        model.endTime = .now.addingTimeInterval(30 * 60)
        model.ruleName = "Work"
        XCTAssertTrue(model.saveRule(placeMode: .gps))
        XCTAssertFalse(SharedState.ruleEnabled)
        XCTAssertNil(model.activeRuleID)
        let id = try XCTUnwrap(model.editingRuleID)
        XCTAssertTrue(model.activateRule(id))
        XCTAssertEqual(model.activeRule?.name, "Work")
        let url = try XCTUnwrap(SharedState.runtimeURL)
        let applied = try Data(contentsOf: url)
        model.ruleName = "Rest"
        XCTAssertTrue(model.saveRule(placeMode: .gps))
        XCTAssertEqual(try Data(contentsOf: url), applied)
        XCTAssertEqual(model.activeRule?.name, "Work")
        XCTAssertTrue(model.hasUnappliedChanges(id))
        model.safetyRelease()
        let released = try Data(contentsOf: url)
        model.ruleName = "Personal time"
        XCTAssertTrue(model.saveRule(placeMode: .gps))
        XCTAssertEqual(try Data(contentsOf: url), released)
        XCTAssertTrue(SharedState.safetyReleased)
    }

    @MainActor
    func testDeletingActiveRuleDoesNotActivateAnotherAfterRestart() async throws {
        let model = AppModel()
        _ = await model.requestScreenTimeAuthorization()
        model.requestTargetSelection()
        model.selectPlace(.init(latitude: 37.5, longitude: 127))
        XCTAssertTrue(model.saveRule(placeMode: .gps))
        let first = try XCTUnwrap(model.editingRuleID)
        XCTAssertTrue(model.activateRule(first))
        model.beginNewRuleDraft()
        model.ruleName = "Second"
        model.selectPlace(.init(latitude: 37.5, longitude: 127))
        XCTAssertTrue(model.saveRule(placeMode: .gps))
        XCTAssertTrue(model.deleteRule(first))
        XCTAssertEqual(model.rules.count, 1)
        XCTAssertNil(SharedState.activeRuleID)
        XCTAssertFalse(SharedState.ruleEnabled)
        let restarted = AppModel()
        XCTAssertNil(restarted.activeRuleID)
        XCTAssertFalse(SharedState.ruleEnabled)
    }
#endif

    func testLegacyMigrationAndExplicitInactiveState() throws {
        let rule = sampleRule()
        SharedState.defaults.set(try JSONEncoder().encode([rule]), forKey: "rules")
        SharedState.defaults.set(try JSONEncoder().encode(rule.selection), forKey: "selection")
        SharedState.defaults.set(true, forKey: "ruleEnabled")
        SharedState.defaults.set(rule.id.uuidString, forKey: "activeRuleID")
        SharedState.defaults.set("nfc", forKey: "placeMode")
        SharedState.defaults.set(0, forKey: "scheduleStart")
        SharedState.defaults.set(1439, forKey: "scheduleEnd")
        XCTAssertEqual(try SharedState.readRuntime().rule?.id, rule.id)
        try SharedState.deactivate()
        XCTAssertNil(try SharedState.readRuntime().rule)
        XCTAssertFalse(SharedState.ruleEnabled)
        XCTAssertTrue(SharedState.defaults.bool(forKey: "ruleEnabled")) // Legacy is preserved, never resurrected.
    }

    func testInvalidLegacyActiveIDDoesNotChooseAnotherRule() throws {
        let rule = sampleRule()
        SharedState.defaults.set(try JSONEncoder().encode([rule]), forKey: "rules")
        SharedState.defaults.set(try JSONEncoder().encode(rule.selection), forKey: "selection")
        SharedState.defaults.set(true, forKey: "ruleEnabled")
        SharedState.defaults.set(UUID().uuidString, forKey: "activeRuleID")
        let state = try SharedState.readRuntime()
        XCTAssertNil(state.rule)
        XCTAssertTrue(state.recoveryPaused)
        XCTAssertEqual(SharedState.rules.count, 1)
    }

    func testCorruptRulesAndUnknownRuntimeAreNotOverwritten() throws {
        SharedState.defaults.set("unexpected storage type", forKey: "rules")
        XCTAssertThrowsError(try SharedState.saveRules([]))
        XCTAssertEqual(SharedState.defaults.string(forKey: "rules"), "unexpected storage type")
        SharedState.defaults.removeObject(forKey: "rules")
        let corrupt = Data("not valid rules".utf8)
        SharedState.defaults.set(corrupt, forKey: "rules")
        XCTAssertThrowsError(try SharedState.saveRules([]))
        XCTAssertEqual(SharedState.defaults.data(forKey: "rules"), corrupt)
        SharedState.defaults.removeObject(forKey: "rules")
        _ = try SharedState.readRuntime()
        let url = try XCTUnwrap(SharedState.runtimeURL)
        var unknown = RuntimeState()
        unknown.schemaVersion = 99
        let original = try JSONEncoder().encode(unknown)
        try original.write(to: url, options: .atomic)
        XCTAssertThrowsError(try SharedState.readRuntime())
        XCTAssertThrowsError(try SharedState.activate(sampleRule()))
        XCTAssertTrue(SharedState.runtime.recoveryPaused)
        XCTAssertEqual(try Data(contentsOf: url), original)
    }

    func testExplicitPlaceStartFreshnessExitRecoveryAndOccurrence() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 9, day: 16, hour: 23)))
        var rule = sampleRule()
        rule.startMinutes = 22 * 60
        rule.endMinutes = 2 * 60
        try SharedState.activate(rule)
        let generation = SharedState.runtime.generation
        XCTAssertThrowsError(try SharedState.startPlaceFocus(generation: generation, at: now, calendar: calendar))
        SharedState.setInsidePlace(true, generation: generation, confirmed: true, observedAt: now.addingTimeInterval(-31))
        XCTAssertThrowsError(try SharedState.startPlaceFocus(generation: generation, at: now, calendar: calendar))
        SharedState.setInsidePlace(true, generation: generation, confirmed: true, observedAt: now)
        XCTAssertFalse(SharedState.runtime.placeFocusConfirmed(at: now, calendar: calendar)) // Arrival alone.
        try SharedState.startPlaceFocus(generation: generation, at: now, calendar: calendar)
        XCTAssertTrue(SharedState.runtime.placeFocusConfirmed(at: now, calendar: calendar))
        XCTAssertTrue(SharedState.runtime.placeFocusConfirmed(at: now.addingTimeInterval(2 * 3600), calendar: calendar))
        XCTAssertFalse(SharedState.runtime.placeFocusConfirmed(at: now.addingTimeInterval(24 * 3600), calendar: calendar))
        SharedState.setInsidePlace(false, generation: generation, confirmed: true)
        SharedState.setInsidePlace(true, generation: generation, confirmed: true, observedAt: now)
        XCTAssertFalse(SharedState.runtime.placeFocusConfirmed(at: now, calendar: calendar)) // Reentry needs tap.
        try SharedState.startPlaceFocus(generation: generation, at: now, calendar: calendar)
        try SharedState.restoreAccess(pause: false, at: now)
        SharedState.setInsidePlace(true, generation: generation, confirmed: true, observedAt: now)
        XCTAssertThrowsError(try SharedState.startPlaceFocus(generation: generation, at: now, calendar: calendar))
        XCTAssertFalse(SharedState.insidePlace)
        let resumedGeneration = SharedState.runtime.generation
        SharedState.setInsidePlace(true, generation: resumedGeneration, confirmed: true, observedAt: now)
        try SharedState.startPlaceFocus(generation: resumedGeneration, at: now, calendar: calendar)
        XCTAssertNil(SharedState.safetyReleaseUntil)
        let occurrence = try XCTUnwrap(SharedState.runtime.placeFocusOccurrenceStart)
        let session = RestrictionPolicy.unlockSessionID(generation: resumedGeneration, occurrenceStart: occurrence)
        XCTAssertTrue(try SharedState.restoreAccess(usingUnlockPass: session, at: now, calendar: calendar))
        XCTAssertNil(SharedState.runtime.placeFocusOccurrenceStart)
        XCTAssertFalse(try SharedState.restoreAccess(usingUnlockPass: session, at: now, calendar: calendar))
        try SharedState.restoreAccess(pause: true)
        SharedState.setInsidePlace(true, generation: SharedState.runtime.generation, confirmed: true, observedAt: now)
        XCTAssertThrowsError(try SharedState.startPlaceFocus(generation: SharedState.runtime.generation, at: now, calendar: calendar))
        try SharedState.activate(rule)
        XCTAssertNil(SharedState.runtime.placeFocusOccurrenceStart)
        XCTAssertFalse(SharedState.insidePlace)
    }

    func testLegacyNFCSessionIsPreservedButCannotActivate() throws {
        let rule = sampleRule(mode: .nfc)
        try SharedState.saveRules([rule])
        let old = RuntimeState(rule: rule, enabled: true, insidePlace: true, nfcActive: true)
        let url = try XCTUnwrap(SharedState.runtimeURL)
        try JSONEncoder().encode(old).write(to: url, options: .atomic)
        XCTAssertFalse(try SharedState.readRuntime().enabled)
        XCTAssertFalse(SharedState.shouldShield)
        XCTAssertEqual(try SharedState.readRules().first?.id, rule.id)
        XCTAssertThrowsError(try SharedState.activate(rule))
        XCTAssertThrowsError(try SharedState.startPlaceFocus(generation: old.generation))
    }

    func testLocationUncertaintyAndHysteresis() {
        let now = Date.now
        func presence(_ distance: Double, _ accuracy: Double, _ inside: Bool = false, age: Double = 0) -> Bool? {
            RestrictionPolicy.placePresence(distance: distance, horizontalAccuracy: accuracy,
                sampleDate: now.addingTimeInterval(-age), now: now, wasInside: inside)
        }
        XCTAssertEqual(presence(130, 20), true)
        XCTAssertEqual(presence(140, 20), false)
        XCTAssertEqual(presence(190, 20, true), true)
        XCTAssertEqual(presence(220, 20, true), true)
        XCTAssertEqual(presence(221, 20, true), false)
        XCTAssertNil(presence(0, 101))
        XCTAssertNil(presence(0, -1))
        XCTAssertNil(presence(0, 10, age: 31))
        XCTAssertNil(presence(0, 10, age: -1))
        XCTAssertNil(presence(.nan, 10))
    }

    func testContendedRuntimeLockFailsWithinBoundedTime() throws {
        _ = try SharedState.readRuntime()
        let url = try XCTUnwrap(SharedState.runtimeURL)
        let bytes = try Data(contentsOf: url)
        let path = url.deletingLastPathComponent().appendingPathComponent("runtime.lock").path
        let descriptor = open(path, O_RDWR)
        XCTAssertGreaterThanOrEqual(descriptor, 0)
        defer { flock(descriptor, LOCK_UN); close(descriptor) }
        XCTAssertEqual(flock(descriptor, LOCK_EX | LOCK_NB), 0)
        let start = Date.now
        XCTAssertThrowsError(try SharedState.readRuntime())
        XCTAssertThrowsError(try SharedState.startPlaceFocus(generation: UUID()))
        XCTAssertLessThan(Date.now.timeIntervalSince(start), 0.5)
        XCTAssertEqual(try Data(contentsOf: url), bytes)
    }

    func testRoomSpiritHomeStatePriority() {
        XCTAssertEqual(RoomSpiritState.home(focused: false, needsAction: false), .idle)
        XCTAssertEqual(RoomSpiritState.home(focused: true, needsAction: false), .focused)
        XCTAssertEqual(RoomSpiritState.home(focused: true, needsAction: true), .needsAction)

        XCTAssertEqual(RoomSpiritMotion.sample(at: 0), .still)
        XCTAssertEqual(RoomSpiritMotion.sample(at: 0.7).breath, 1, accuracy: 0.001)
        XCTAssertEqual(RoomSpiritMotion.sample(at: 2.1).breath, -1, accuracy: 0.001)
    }

    func testRoomieResultsNeverOverrideFocusRecoveryOrErrors() {
        XCTAssertEqual(RoomSpiritState.home(focused: true, needsAction: false, reaction: .celebrating), .focused)
        XCTAssertEqual(RoomSpiritState.home(focused: false, needsAction: true, recovered: true, reaction: .confirmed), .needsAction)
        XCTAssertEqual(RoomSpiritState.home(focused: false, needsAction: false, recovered: true, reaction: .returning), .recovered)
        XCTAssertEqual(RoomSpiritState.home(focused: false, needsAction: false, reaction: .celebrating), .celebrating)
        for state in [RoomSpiritState.focused, .needsAction, .recovered, .celebrating, .returning] {
            XCTAssertFalse(state.allowsAmbientMotion)
        }
        XCTAssertTrue(RoomSpiritState.working.allowsAmbientMotion)
        XCTAssertTrue(RoomSpiritState.idle.allowsAmbientMotion)
        XCTAssertLessThan(RoomSpiritReaction.sample(state: .celebrating, elapsed: 0.4).lift, 0)
        for state in [RoomSpiritState.welcome, .guiding, .confirmed, .celebrating, .returning, .finished, .recovered] {
            XCTAssertEqual(RoomSpiritReaction.sample(state: state, elapsed: -1), .init())
            XCTAssertEqual(RoomSpiritReaction.sample(state: state, elapsed: 1), .init())
        }
        XCTAssertEqual(RoomSpiritReaction.sample(state: .focused, elapsed: 0.4), .init())
        XCTAssertEqual(RoomSpiritReaction.sample(state: .needsAction, elapsed: 0.42), .init())
    }

    func testRestrictionPriorityAndOvernightSchedule() throws {
        XCTAssertFalse(RestrictionPolicy.shouldShield(ruleEnabled: true, hasSelection: true,
            scheduleActive: true, placeMode: .gps, insidePlace: true,
            placeFocusConfirmed: false, safetyReleased: false))
        XCTAssertTrue(RestrictionPolicy.shouldShield(
            ruleEnabled: true,
            hasSelection: true,
            scheduleActive: true,
            placeMode: .gps,
            insidePlace: true,
            placeFocusConfirmed: true,
            safetyReleased: false
        ))
        XCTAssertFalse(RestrictionPolicy.shouldShield(
            ruleEnabled: true,
            hasSelection: true,
            scheduleActive: true,
            placeMode: .gps,
            insidePlace: true,
            placeFocusConfirmed: true,
            safetyReleased: true
        ))
        XCTAssertFalse(RestrictionPolicy.shouldShield(
            ruleEnabled: false,
            hasSelection: true,
            scheduleActive: true,
            placeMode: .gps,
            insidePlace: true,
            placeFocusConfirmed: true,
            safetyReleased: false
        ))
        XCTAssertFalse(RestrictionPolicy.shouldShield(
            ruleEnabled: true,
            hasSelection: true,
            scheduleActive: true,
            placeMode: .gps,
            insidePlace: false,
            placeFocusConfirmed: true,
            safetyReleased: false
        ))
        XCTAssertFalse(RestrictionPolicy.shouldShield(
            ruleEnabled: true,
            hasSelection: true,
            scheduleActive: true,
            placeMode: .nfc,
            insidePlace: false,
            placeFocusConfirmed: true,
            safetyReleased: false
        ))

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let late = try XCTUnwrap(calendar.date(from: DateComponents(
            calendar: calendar,
            timeZone: calendar.timeZone,
            year: 2026,
            month: 9,
            day: 4,
            hour: 23
        )))
        let early = try XCTUnwrap(calendar.date(from: DateComponents(
            calendar: calendar,
            timeZone: calendar.timeZone,
            year: 2026,
            month: 9,
            day: 5,
            hour: 1
        )))

        XCTAssertTrue(RestrictionPolicy.isScheduleActive(
            at: late,
            startMinutes: 22 * 60,
            endMinutes: 2 * 60,
            calendar: calendar
        ))
        XCTAssertTrue(RestrictionPolicy.isScheduleActive(
            at: early,
            startMinutes: 22 * 60,
            endMinutes: 2 * 60,
            calendar: calendar
        ))
        XCTAssertEqual(RestrictionPolicy.intervalMinutes(start: 23 * 60, end: 15), 75)
        XCTAssertEqual(
            RestrictionPolicy.nextScheduleEnd(after: late, endMinutes: 2 * 60, calendar: calendar),
            calendar.date(from: DateComponents(
                calendar: calendar,
                timeZone: calendar.timeZone,
                year: 2026,
                month: 9,
                day: 5,
                hour: 2
            ))
        )

        let lateStart = try XCTUnwrap(RestrictionPolicy.scheduleOccurrenceStart(
            at: late, startMinutes: 22 * 60, endMinutes: 2 * 60, calendar: calendar))
        let earlyStart = try XCTUnwrap(RestrictionPolicy.scheduleOccurrenceStart(
            at: early, startMinutes: 22 * 60, endMinutes: 2 * 60, calendar: calendar))
        XCTAssertEqual(lateStart, earlyStart)
        let generation = UUID()
        let sessionID = RestrictionPolicy.unlockSessionID(generation: generation, occurrenceStart: lateStart)
        XCTAssertEqual(sessionID.count, 64)
        XCTAssertEqual(sessionID, RestrictionPolicy.unlockSessionID(generation: generation, occurrenceStart: earlyStart))
        XCTAssertNotEqual(sessionID, RestrictionPolicy.unlockSessionID(generation: UUID(), occurrenceStart: earlyStart))
    }
}
