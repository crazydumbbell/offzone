import FirebaseAnalytics
import FirebaseCore
import CoreLocation
import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings
import SwiftUI
import UIKit
import UserNotifications

func roomString(_ key: String, _ arguments: CVarArg...) -> String {
    let format = Bundle.main.localizedString(forKey: key, value: key, table: nil)
    return String(format: format, locale: .current, arguments: arguments)
}

@MainActor
final class AppModel: NSObject, ObservableObject {
    @Published var selection: FamilyActivitySelection
    @Published var startTime: Date
    @Published var endTime: Date
    @Published var ruleName: String
    @Published private(set) var rules: [FocusRule]
    @Published private(set) var activeRuleID: UUID?
    @Published private(set) var editingRuleID: UUID?
    @Published private(set) var draftHasPlace: Bool
    @Published private(set) var draftPlaceCoordinate: CLLocationCoordinate2D?
    @Published private(set) var isCheckingPlace = false
    @Published var preventAppRemoval = false
    @Published var isPickerPresented = false
    @Published private(set) var screenTimeStatus = AuthorizationCenter.shared.authorizationStatus
    @Published private(set) var locationStatus = CLAuthorizationStatus.notDetermined
    @Published private(set) var isFocused = false
    @Published private(set) var placeMonitoringNeedsAttention = false
    @Published private(set) var notificationStatus: UNAuthorizationStatus = .notDetermined
    @Published private(set) var zoneNotificationsEnabled = SharedState.defaults.bool(forKey: RestrictionNotifications.enabledKey)
#if targetEnvironment(simulator)
    @Published private var simulatorScreenTimeAuthorized: Bool
    @Published private var simulatorSelectionCount: Int
#endif
    @Published private(set) var errorMessage: String?
    @Published private(set) var message = "" {
        didSet { errorMessage = nil }
    }

    private func reportError(_ text: String) {
        message = text
        errorMessage = text
    }

    private let locationManager = CLLocationManager()
    private var locationServiceSession: CLServiceSession?
    private var locationDiagnosticTask: Task<Void, Never>?
    private var placeMonitor: CLMonitor?
    private var monitoredPlaceIdentifier: String?
    private var placeMonitorTask: Task<Void, Never>?
    private var protectedDataTask: Task<Void, Never>?
    private var scheduleRefreshTask: Task<Void, Never>?
    private var locationCheckGeneration: UUID?
    private var pendingStartGeneration: UUID?
    private var locationCheckTask: Task<Void, Never>?
    private var monitorDiagnosticActive = false
    private var locationAutomationReady = false
    private var openingPlaceMonitor = false
    private var pendingPlaceReplacement = false
    private var awaitingPlaceAuthorization = false
    private var captureCurrentLocation = false
    private var requestedAlwaysThisRun = false

    override init() {
        let state = SharedState.runtime
        let savedRules = SharedState.rules
        let active = state.rule
        selection = active?.selection ?? FamilyActivitySelection()
        startTime = Self.date(minutes: active?.startMinutes ?? 540)
        endTime = Self.date(minutes: active?.endMinutes ?? 1080)
        ruleName = active?.name ?? roomString("My time")
        rules = savedRules
        activeRuleID = active?.id
        editingRuleID = active?.id
        draftHasPlace = active?.hasPlace ?? false
        draftPlaceCoordinate = active.flatMap { rule in
            rule.hasPlace ? CLLocationCoordinate2D(latitude: rule.placeLatitude, longitude: rule.placeLongitude) : nil
        }
        preventAppRemoval = active?.preventAppRemoval == true
#if targetEnvironment(simulator)
        simulatorScreenTimeAuthorized = state.enabled || !savedRules.isEmpty
        simulatorSelectionCount = active?.targetCount ?? 0
#endif
        super.init()

        UNUserNotificationCenter.current().delegate = RestrictionNotifications.shared
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationStatus = locationManager.authorizationStatus
        if SharedState.hasPlace {
            SharedState.setInsidePlace(false)
        }
        refresh()
    }

    var screenTimeAuthorized: Bool {
#if targetEnvironment(simulator)
        if simulatorScreenTimeAuthorized { return true }
#endif
        if screenTimeStatus == .approved { return true }
        if #available(iOS 26.4, *), screenTimeStatus == .approvedWithDataAccess { return true }
        return false
    }

    var selectionCount: Int {
        let actualCount = selection.applicationTokens.count
            + selection.categoryTokens.count
            + selection.webDomainTokens.count
#if targetEnvironment(simulator)
        return actualCount == 0 ? simulatorSelectionCount : actualCount
#else
        return actualCount
#endif
    }

    var activeRule: FocusRule? { SharedState.appliedRule }

    func hasUnappliedChanges(_ id: UUID) -> Bool {
        guard let saved = rules.first(where: { $0.id == id }), let applied = activeRule, applied.id == id else { return false }
        return saved.name != applied.name || saved.startMinutes != applied.startMinutes
            || saved.endMinutes != applied.endMinutes || saved.placeMode != applied.placeMode
            || saved.hasPlace != applied.hasPlace || saved.placeLatitude != applied.placeLatitude
            || saved.placeLongitude != applied.placeLongitude
            || saved.selection.applicationTokens != applied.selection.applicationTokens
            || saved.selection.categoryTokens != applied.selection.categoryTokens
            || saved.selection.webDomainTokens != applied.selection.webDomainTokens
            || saved.targetCount != applied.targetCount
            || (saved.preventAppRemoval == true) != (applied.preventAppRemoval == true)
    }

    var currentStatusDetail: String? {
        let state = SharedState.runtime
        guard state.enabled, !state.recoveryPaused, let rule = state.rule else { return nil }
        if let until = state.safetyReleaseUntil, until > .now {
            return rule.placeMode == .gps
                ? roomString("Until %@", until.formatted(date: .abbreviated, time: .shortened))
                : nil
        }
        if !SharedState.scheduleActive {
            return roomString("Starts at %@", Self.date(minutes: rule.startMinutes).formatted(date: .omitted, time: .shortened))
        }
        return isFocused
            ? roomString("Until %@", Self.date(minutes: rule.endMinutes).formatted(date: .omitted, time: .shortened))
            : nil
    }

    var safetyReleaseDescription: String {
        let detail: String
        if !SharedState.ruleEnabled || !SharedState.scheduleActive || !screenTimeAuthorized
            || placeMonitoringNeedsAttention || SharedState.rulesStorageError {
            detail = roomString("Rules will pause until you activate them again.")
        } else {
            detail = roomString("End this session. Tap Start focus when you are ready to begin again.")
        }
        return activeRule?.preventAppRemoval == true
            ? detail + "\n" + roomString("This also removes this rule’s app deletion restriction.")
            : detail
    }

    var unlockSessionID: String? {
        let state = SharedState.runtime
        guard isFocused, state.enabled, !state.recoveryPaused, let rule = state.rule,
              let occurrence = RestrictionPolicy.scheduleOccurrenceStart(
                at: .now, startMinutes: rule.startMinutes, endMinutes: rule.endMinutes)
        else { return nil }
        return RestrictionPolicy.unlockSessionID(generation: state.generation, occurrenceStart: occurrence)
    }

    func requestTargetSelection() {
#if targetEnvironment(simulator)
        simulatorSelectionCount = 3
        message = roomString("Selected three demo items for Simulator.")
#else
        isPickerPresented = true
#endif
    }

    var placeConfigured: Bool { draftHasPlace }
    var gpsPlaceReady: Bool {
#if targetEnvironment(simulator)
        placeConfigured
#else
        placeConfigured
            && locationStatus == .authorizedAlways
            && locationManager.accuracyAuthorization == .fullAccuracy
#endif
    }
    var gpsNeedsSettings: Bool {
#if targetEnvironment(simulator)
        false
#else
        locationStatus == .denied
            || locationStatus == .restricted
            || locationManager.accuracyAuthorization != .fullAccuracy
            || (placeConfigured && locationStatus == .authorizedWhenInUse && requestedAlwaysThisRun)
#endif
    }

    var locationStatusText: String {
        if placeConfigured && locationManager.accuracyAuthorization != .fullAccuracy {
            return roomString("Precise Location needed")
        }
        if placeConfigured && placeMonitoringNeedsAttention && locationStatus == .authorizedAlways {
            return roomString("Check place detection")
        }
        switch locationStatus {
        case .authorizedAlways:
            return placeConfigured ? roomString("150 m place saved · Always allowed") : roomString("Always allowed")
        case .authorizedWhenInUse:
            return placeConfigured ? roomString("Place saved · Always access needed") : roomString("While Using only")
        case .denied, .restricted:
            return roomString("Location access needed")
        case .notDetermined:
            return roomString("Not connected yet")
        @unknown default:
            return roomString("Check Location status")
        }
    }

    var screenTimeStatusText: String {
        switch screenTimeStatus {
        case .approved: return roomString("Approved")
        case .denied: return roomString("Approval needed")
        case .notDetermined: return roomString("Not connected yet")
        default: return screenTimeAuthorized ? roomString("Approved") : roomString("Check status")
        }
    }

    var currentStatusTitle: String {
        if SharedState.rulesStorageError { return roomString("Check your rules") }
        if SharedState.runtime.recoveryPaused { return roomString("Paused") }
        if !screenTimeAuthorized { return roomString("Setup needed") }
        if isFocused { return roomString("In focus") }
        if SharedState.safetyReleased { return roomString("Access restored") }
        if !SharedState.ruleEnabled { return rules.isEmpty ? roomString("Create your first rule") : roomString("Ready when you are") }
        if SharedState.placeMode == .gps && placeMonitoringNeedsAttention { return roomString("Check Location Settings") }
        if !SharedState.scheduleActive { return roomString("Scheduled") }
        if SharedState.placeMode == .gps && !SharedState.insidePlace { return roomString("Check your location") }
        return roomString("Ready")
    }

    func requestScreenTimeAuthorization() async -> Bool {
#if targetEnvironment(simulator)
        simulatorScreenTimeAuthorized = true
        message = roomString("Screen Time isn't available in Simulator, but you can preview the onboarding flow.")
        return true
#else
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            message = roomString("Screen Time is connected.")
        } catch {
            reportError(roomString("Couldn't connect Screen Time: %@", error.localizedDescription))
        }
        refresh()
        return screenTimeAuthorized
#endif
    }

    var notificationStatusText: String {
        guard zoneNotificationsEnabled else { return roomString("Off") }
        switch notificationStatus {
        case .authorized, .provisional, .ephemeral: return roomString("On")
        case .denied: return roomString("Allow in Settings")
        case .notDetermined: return roomString("Permission needed")
        @unknown default: return roomString("Check notification settings")
        }
    }

    func refreshNotificationSettings() async {
        notificationStatus = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    func setZoneNotificationsEnabled(_ enabled: Bool) async {
        zoneNotificationsEnabled = enabled
        SharedState.defaults.set(enabled, forKey: RestrictionNotifications.enabledKey)
        if enabled {
            do {
                _ = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
            } catch { reportError(roomString("Couldn't enable notifications: %@", error.localizedDescription)) }
        } else {
            let center = UNUserNotificationCenter.current()
            let pending = await center.pendingNotificationRequests()
            center.removePendingNotificationRequests(withIdentifiers: pending.map(\.identifier).filter { $0.hasPrefix("roomdns.event.") })
        }
        await refreshNotificationSettings()
    }

    func requestCurrentPlace() {
        cancelArrivalCheck()
        awaitingPlaceAuthorization = true
        captureCurrentLocation = true
        message = roomString("Checking your current location.")

        switch locationManager.authorizationStatus {
        case .notDetermined:
            locationManager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse:
            requestAlwaysIfNeeded()
            locationManager.requestLocation()
        case .authorizedAlways:
            ensureAlwaysLocationSession()
            locationManager.requestLocation()
        case .denied, .restricted:
            awaitingPlaceAuthorization = false
            captureCurrentLocation = false
            reportError(roomString("Choose Always in Location Settings so Offzone can notice when you arrive and leave."))
        @unknown default:
            awaitingPlaceAuthorization = false
            captureCurrentLocation = false
            reportError(roomString("Couldn't check Location access."))
        }
        refresh()
    }

    func selectPlace(_ coordinate: CLLocationCoordinate2D) {
        guard CLLocationCoordinate2DIsValid(coordinate) else { return }
        cancelPlaceLookup()
        draftPlaceCoordinate = coordinate
        draftHasPlace = true
        message = roomString("Place selected. Save this rule, then activate it from Home.")
    }

    func requestPlaceAuthorization() {
        awaitingPlaceAuthorization = true
        captureCurrentLocation = false

        switch locationManager.authorizationStatus {
        case .notDetermined:
            locationManager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse:
            requestAlwaysIfNeeded()
        case .authorizedAlways:
            awaitingPlaceAuthorization = false
            ensureAlwaysLocationSession()
        case .denied, .restricted:
            awaitingPlaceAuthorization = false
            reportError(roomString("Choose Always in Location Settings so Offzone can notice when you arrive and leave."))
        @unknown default:
            awaitingPlaceAuthorization = false
            reportError(roomString("Couldn't check Location access."))
        }
        refresh()
    }

    @discardableResult
    func saveRule(placeMode: PlaceMode) -> Bool {
        guard selectionCount > 0 else {
            reportError(roomString("Choose at least one app, category, or website."))
            return false
        }
        guard placeMode == .gps, draftHasPlace else {
            reportError(roomString("Choose a place before saving this rule."))
            return false
        }
        let start = Self.minutes(from: startTime)
        let end = Self.minutes(from: endTime)
        guard RestrictionPolicy.intervalMinutes(start: start, end: end) >= 15 else {
            reportError(roomString("Choose a schedule of at least 15 minutes."))
            return false
        }
        let name = ruleName.trimmingCharacters(in: .whitespacesAndNewlines)
        let rule = FocusRule(
            id: editingRuleID ?? UUID(), name: name.isEmpty ? roomString("My time") : name,
            selection: selection, targetCount: selectionCount,
            startMinutes: start, endMinutes: end, placeMode: placeMode,
            hasPlace: placeMode == .gps && draftHasPlace,
            placeLatitude: draftPlaceCoordinate?.latitude ?? 0,
            placeLongitude: draftPlaceCoordinate?.longitude ?? 0,
            nfcConfigured: false,
            preventAppRemoval: preventAppRemoval)
        do {
            var updated = try SharedState.readRules()
            if let index = updated.firstIndex(where: { $0.id == rule.id }) { updated[index] = rule }
            else { updated.append(rule) }
            try SharedState.saveRules(updated)
            rules = updated
            editingRuleID = rule.id
            ruleName = rule.name
            message = roomString("Saved. Activate the rule to apply it. Your current session has not changed.")
            return true
        } catch {
            reportError(roomString("Could not save the rule: %@", error.localizedDescription))
            return false
        }
    }

    @discardableResult
    func deleteRule(_ id: UUID) -> Bool {
        do {
            let updated = try SharedState.readRules().filter { $0.id != id }
            if activeRuleID == id {
                cancelArrivalCheck()
                try SharedState.deactivate()
                DeviceActivityCenter().stopMonitoring([.roomDNS])
                stopLocationAutomation()
            }
            try SharedState.saveRules(updated)
            rules = updated
            if editingRuleID == id { beginNewRuleDraft() }
            refresh()
            message = roomString("Rule deleted. No other rule was activated.")
            return true
        } catch {
            SharedState.clearRestrictions()
            refresh()
            reportError(roomString("Could not delete the rule: %@", error.localizedDescription))
            return false
        }
    }

    func resetRuleDraft() {
        cancelArrivalCheck()
        guard let activeRule else { beginNewRuleDraft(); return }
        loadDraft(activeRule)
        message = roomString("Choose a place and schedule, then start focus when you arrive.")
        refresh()
    }

    func beginNewRuleDraft() {
        cancelArrivalCheck()
        cancelPlaceLookup()
        let activeRule = rules.first { $0.id == activeRuleID }
        editingRuleID = nil
        ruleName = roomString("New focus")
        selection = activeRule?.selection ?? FamilyActivitySelection()
        startTime = Self.date(minutes: activeRule?.startMinutes ?? 9 * 60)
        endTime = Self.date(minutes: activeRule?.endMinutes ?? 18 * 60)
        draftHasPlace = false
        draftPlaceCoordinate = nil
        preventAppRemoval = false
#if targetEnvironment(simulator)
        simulatorSelectionCount = activeRule?.targetCount ?? 0
#endif
    }

    func loadRuleForEditing(_ id: UUID) {
        cancelArrivalCheck()
        guard let rule = rules.first(where: { $0.id == id }) else { return }
        loadDraft(rule)
    }

    @discardableResult
    func activateRule(_ id: UUID) -> Bool {
        guard let rule = rules.first(where: { $0.id == id }) else { return false }
        guard rule.placeMode == .gps, rule.hasPlace else {
            reportError(roomString("Choose a place for this saved rule before activating it."))
            return false
        }
        screenTimeStatus = AuthorizationCenter.shared.authorizationStatus
        guard screenTimeAuthorized else {
            reportError(roomString("Allow Screen Time before activating this rule."))
            return false
        }
#if !targetEnvironment(simulator)
        guard rule.placeMode != .gps || (rule.hasPlace
            && locationManager.authorizationStatus == .authorizedAlways
            && locationManager.accuracyAuthorization == .fullAccuracy) else {
            reportError(roomString("GPS rules need Always and Precise Location permissions."))
            return false
        }
#endif
        let previous: RuntimeState
        do { previous = try SharedState.readRuntime(); _ = try SharedState.readRules() }
        catch { reportError(roomString("Could not activate the rule: %@", error.localizedDescription)); return false }
        do {
            try registerSchedule(for: rule)
            cancelArrivalCheck()
            try SharedState.activate(rule)
            loadDraft(rule)
            refresh()
            if rule.placeMode == .gps { startPlaceMonitoring(replacingSavedCondition: true) }
            checkPlaceArrival()
            message = roomString("Rule ready. Check your location, then tap Start focus.")
            // Successful rule activation, not proof that a focus session completed.
            if FirebaseApp.app() != nil { Analytics.logEvent("rule_activated", parameters: nil) }
            return true
        } catch {
            // Restore the previous OS schedule if publishing the new configuration failed.
            var restored = true
            if previous.enabled, let oldRule = previous.rule {
                do { try registerSchedule(for: oldRule) }
                catch { restored = false }
            } else { DeviceActivityCenter().stopMonitoring([.roomDNS]) }
            if restored { refresh() }
            else {
                try? SharedState.restoreAccess(pause: true)
                SharedState.clearRestrictions()
                isFocused = false
            }
            reportError(roomString("Could not activate the rule: %@", error.localizedDescription))
            return false
        }
    }

    private func registerSchedule(for rule: FocusRule) throws {
#if !targetEnvironment(simulator)
        var start = DateComponents()
        start.calendar = .current
        start.timeZone = .current
        start.hour = rule.startMinutes / 60
        start.minute = rule.startMinutes % 60
        var end = DateComponents()
        end.calendar = .current
        end.timeZone = .current
        end.hour = rule.endMinutes / 60
        end.minute = rule.endMinutes % 60
        try DeviceActivityCenter().startMonitoring(.roomDNS, during: DeviceActivitySchedule(
            intervalStart: start, intervalEnd: end, repeats: true))
#endif
    }

    private func loadDraft(_ rule: FocusRule) {
        cancelPlaceLookup()
        editingRuleID = rule.id
        ruleName = rule.name
        selection = rule.selection
        startTime = Self.date(minutes: rule.startMinutes)
        endTime = Self.date(minutes: rule.endMinutes)
        draftHasPlace = rule.hasPlace
        draftPlaceCoordinate = rule.hasPlace
            ? CLLocationCoordinate2D(latitude: rule.placeLatitude, longitude: rule.placeLongitude)
            : nil
        preventAppRemoval = rule.preventAppRemoval == true
#if targetEnvironment(simulator)
        simulatorSelectionCount = rule.targetCount
#endif
    }

    private func cancelPlaceLookup() {
        awaitingPlaceAuthorization = false
        captureCurrentLocation = false
    }

    var canStartPlaceFocus: Bool {
        let state = SharedState.runtime
        guard !isCheckingPlace, !isFocused, screenTimeAuthorized,
              state.enabled, !state.recoveryPaused, state.rule?.placeMode == .gps,
              SharedState.scheduleActive, state.insidePlace,
              let observed = state.placeObservedAt,
              (0...30).contains(Date.now.timeIntervalSince(observed)) else { return false }
        return !placeMonitoringNeedsAttention
    }

    var placeArrivalText: String {
        let state = SharedState.runtime
        if isCheckingPlace { return roomString("Checking your location…") }
        guard state.enabled, !state.recoveryPaused, state.rule?.placeMode == .gps else {
            return roomString("Activate a place rule to begin.")
        }
        if isFocused { return roomString("Focus is on. Leaving the area or the schedule ending will end this session.") }
        if !SharedState.scheduleActive { return roomString("Start focus during your saved schedule.") }
        if placeMonitoringNeedsAttention { return roomString("Check Always and Precise Location in Settings.") }
        guard let observed = state.placeObservedAt,
              (0...30).contains(Date.now.timeIntervalSince(observed)) else {
            return roomString("Check your location to confirm you are near the saved place.")
        }
        return state.insidePlace
            ? roomString("You are near your saved place. Tap Start focus when you are ready.")
            : roomString("Move closer to your saved place, then check your location again.")
    }

    func checkPlaceArrival() {
        requestArrivalCheck(startFocus: false)
    }

    func startPlaceFocus() {
        requestArrivalCheck(startFocus: true)
    }

    private func requestArrivalCheck(startFocus: Bool) {
        guard !isCheckingPlace else { return }
        let state = SharedState.runtime
        guard state.enabled, !state.recoveryPaused,
              let rule = state.rule, rule.placeMode == .gps, rule.hasPlace else {
            reportError(roomString("Activate a place rule to begin."))
            return
        }
        guard screenTimeAuthorized else {
            reportError(roomString("Allow Screen Time before activating this rule."))
            return
        }
        guard locationManager.authorizationStatus == .authorizedAlways,
              locationManager.accuracyAuthorization == .fullAccuracy else {
            failOpenLocation(roomString("Check Always and Precise Location in Settings."))
            return
        }
        cancelPlaceLookup()
        locationCheckGeneration = state.generation
        pendingStartGeneration = startFocus ? state.generation : nil
        isCheckingPlace = true
        locationManager.requestLocation()
        locationCheckTask?.cancel()
        locationCheckTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(15))
            guard !Task.isCancelled, let self, self.isCheckingPlace else { return }
            self.cancelArrivalCheck()
            self.failOpenLocation(roomString("Location could not be confirmed. Check again near a window or outside."))
        }
    }

    private func cancelArrivalCheck() {
        locationCheckTask?.cancel()
        locationCheckTask = nil
        locationCheckGeneration = nil
        pendingStartGeneration = nil
        isCheckingPlace = false
    }

    private func updatePlaceArrival(_ location: CLLocation) {
        guard let generation = locationCheckGeneration else { return }
        let shouldStart = pendingStartGeneration == generation
        cancelArrivalCheck()
        let state = SharedState.runtime
        guard state.generation == generation, state.enabled, !state.recoveryPaused,
              let rule = state.rule, rule.placeMode == .gps else { return }
        guard locationManager.accuracyAuthorization == .fullAccuracy,
              locationManager.authorizationStatus == .authorizedAlways else {
            failOpenLocation(roomString("Check Always and Precise Location in Settings."))
            return
        }
        let center = CLLocation(latitude: rule.placeLatitude, longitude: rule.placeLongitude)
        guard let inside = RestrictionPolicy.placePresence(
            distance: location.distance(from: center), horizontalAccuracy: location.horizontalAccuracy,
            sampleDate: location.timestamp, now: .now, wasInside: state.insidePlace) else {
            failOpenLocation(roomString("Location could not be confirmed. Check again near a window or outside."))
            return
        }
        monitorDiagnosticActive = false
        placeMonitoringNeedsAttention = false
        SharedState.setInsidePlace(inside, generation: generation, confirmed: true, observedAt: location.timestamp)
        if shouldStart {
            do {
                try SharedState.startPlaceFocus(generation: generation)
                message = roomString("Focus started. You can restore access at any time.")
            } catch {
                reportError(roomString("Could not start focus: %@", error.localizedDescription))
            }
        }
        refresh()
    }

    func safetyRelease() {
        cancelArrivalCheck()
        let pause = !SharedState.ruleEnabled || !SharedState.scheduleActive || !screenTimeAuthorized
            || placeMonitoringNeedsAttention || SharedState.rulesStorageError
        do {
            try SharedState.restoreAccess(pause: pause)
            refresh()
            message = roomString("Restrictions cleared. Check that your apps open.")
        } catch {
            SharedState.clearRestrictions()
            isFocused = false
            reportError(roomString("Access was cleared, but recovery could not be saved. Restrictions may return. Try restoring access again: %@", error.localizedDescription))
        }
    }

    func release(usingUnlockPass sessionID: String) -> Bool {
        cancelArrivalCheck()
        do {
            guard try SharedState.restoreAccess(usingUnlockPass: sessionID) else {
                refresh()
                reportError(roomString("That pass was linked to an earlier session. This session is still active."))
                return false
            }
            refresh()
            message = roomString("Restrictions cleared. Check that your apps open.")
            return true
        } catch {
            SharedState.clearRestrictions()
            isFocused = false
            reportError(roomString("Access was cleared, but recovery could not be saved. Restrictions may return. Try restoring access again: %@", error.localizedDescription))
            return false
        }
    }

    func refresh() {
        Task { [weak self] in await self?.refreshNotificationSettings() }
        screenTimeStatus = AuthorizationCenter.shared.authorizationStatus
        locationStatus = locationManager.authorizationStatus
        guard let state = try? SharedState.readRuntime(), !SharedState.rulesStorageError else {
            try? SharedState.restoreAccess(pause: true)
            SharedState.clearRestrictions()
            isFocused = false
            reportError(roomString("Saved data needs attention. Originals are kept and restrictions are cleared."))
            return
        }
        activeRuleID = state.rule?.id
        synchronizeLocationAutomation()
        if screenTimeAuthorized { SharedState.reconcileRestrictions() }
        else {
            try? SharedState.restoreAccess(pause: true)
            SharedState.clearRestrictions()
        }
        let current = SharedState.runtime
#if targetEnvironment(simulator)
        isFocused = screenTimeAuthorized && RestrictionPolicy.shouldShield(
            ruleEnabled: current.enabled && !current.recoveryPaused && current.placeFocusConfirmed(at: .now),
            hasSelection: (current.rule?.targetCount ?? 0) > 0,
            scheduleActive: SharedState.scheduleActive, placeMode: current.rule?.placeMode ?? .gps,
            insidePlace: current.insidePlace, placeFocusConfirmed: current.placeFocusConfirmed(at: .now),
            safetyReleased: current.safetyReleaseUntil.map { $0 > Date.now } ?? false)
#else
        isFocused = screenTimeAuthorized && current.shouldShield(at: .now)
#endif
        armScheduleBoundaryRefresh()
    }

    private func requestAlwaysIfNeeded() {
        guard !requestedAlwaysThisRun else { return }
        requestedAlwaysThisRun = true
        locationManager.requestAlwaysAuthorization()
    }

    private func handleAuthorizationChange(_ status: CLAuthorizationStatus) {
        locationStatus = status
        guard awaitingPlaceAuthorization else {
            refresh()
            return
        }

        switch status {
        case .authorizedWhenInUse:
            requestAlwaysIfNeeded()
            if captureCurrentLocation { locationManager.requestLocation() }
        case .authorizedAlways:
            awaitingPlaceAuthorization = false
            ensureAlwaysLocationSession()
            if captureCurrentLocation { locationManager.requestLocation() }
        case .denied, .restricted:
            awaitingPlaceAuthorization = false
            captureCurrentLocation = false
            reportError(roomString("Location access is off, so automatic place detection can't start."))
        case .notDetermined:
            break
        @unknown default:
            awaitingPlaceAuthorization = false
            captureCurrentLocation = false
        }
        refresh()
    }

    private func savePlace(_ location: CLLocation) {
        awaitingPlaceAuthorization = false
        captureCurrentLocation = false
        guard locationManager.accuracyAuthorization == .fullAccuracy else {
            reportError(roomString("Turn on Precise Location before activating this rule. Uncertain location will not start restrictions."))
            refresh()
            return
        }
        guard CLLocationCoordinate2DIsValid(location.coordinate),
              (0...30).contains(Date.now.timeIntervalSince(location.timestamp)),
              location.horizontalAccuracy >= 0, location.horizontalAccuracy <= 100 else {
            reportError(roomString("The location reading isn't accurate enough. Try again near a window or outside."))
            return
        }

        draftPlaceCoordinate = location.coordinate
        draftHasPlace = true
        message = roomString("Place selected. Adjust the pin if needed. Location detection is approximate.")
        refresh()
    }

    private func startPlaceMonitoring(replacingSavedCondition: Bool = false) {
        guard SharedState.ruleEnabled, SharedState.hasPlace,
              locationManager.authorizationStatus == .authorizedAlways,
              locationManager.accuracyAuthorization == .fullAccuracy,
              UIApplication.shared.isProtectedDataAvailable
        else { return }

        pendingPlaceReplacement = pendingPlaceReplacement || replacingSavedCondition
        if placeMonitor != nil && monitoredPlaceIdentifier == SharedState.placeIdentifier && !pendingPlaceReplacement { return }
        guard !openingPlaceMonitor else { return }
        openingPlaceMonitor = true
        Task { [weak self] in
            await self?.configurePlaceMonitor()
        }
    }

    private func configurePlaceMonitor() async {
        let generation = SharedState.runtime.generation
        let identifier = "place-" + generation.uuidString
        let monitor: CLMonitor
        if let placeMonitor { monitor = placeMonitor }
        else {
            monitor = await CLMonitor("RoomDNSPlaces")
            placeMonitor = monitor
        }
        guard SharedState.runtime.generation == generation, SharedState.ruleEnabled else {
            openingPlaceMonitor = false
            startPlaceMonitoring(replacingSavedCondition: true)
            return
        }
        if placeMonitorTask == nil { listenForPlaceEvents(from: monitor) }
        pendingPlaceReplacement = false
        let existing = await monitor.identifiers
        for old in existing where old != identifier {
            await monitor.remove(old)
        }
        guard SharedState.runtime.generation == generation, SharedState.ruleEnabled else {
            openingPlaceMonitor = false
            startPlaceMonitoring(replacingSavedCondition: true)
            return
        }
        if !existing.contains(identifier) || monitoredPlaceIdentifier != identifier {
            let rule = SharedState.appliedRule
            await monitor.add(CLMonitor.CircularGeographicCondition(
                center: CLLocationCoordinate2D(latitude: rule?.placeLatitude ?? 0, longitude: rule?.placeLongitude ?? 0),
                radius: 200), identifier: identifier)
        }
        guard SharedState.runtime.generation == generation, SharedState.ruleEnabled else {
            await monitor.remove(identifier)
            openingPlaceMonitor = false
            startPlaceMonitoring(replacingSavedCondition: true)
            return
        }
        monitoredPlaceIdentifier = identifier
        if let record = await monitor.record(for: identifier) { handlePlaceEvent(record.lastEvent) }
        openingPlaceMonitor = false
        if pendingPlaceReplacement { startPlaceMonitoring(replacingSavedCondition: true) }
    }

    private func listenForPlaceEvents(from monitor: CLMonitor) {
        placeMonitorTask = Task { [weak self] in
            do {
                for try await event in await monitor.events {
                    guard !Task.isCancelled else { return }
                    self?.handlePlaceEvent(event)
                }
            } catch is CancellationError {
                return
            } catch {
                self?.placeMonitor = nil
                self?.placeMonitorTask = nil
                self?.openingPlaceMonitor = false
                self?.failOpenLocation(roomString("Place detection stopped, so the location-based shield is open."))
            }
        }
    }

    private func handlePlaceEvent(_ event: CLMonitor.Event) {
        let state = SharedState.runtime
        guard state.rule?.placeMode == .gps, state.enabled, !state.recoveryPaused,
              event.identifier == "place-" + state.generation.uuidString else { return }
        if event.authorizationRequestInProgress {
            failOpenLocation(roomString("Waiting for your Location choice."))
            return
        }
        let unavailable = event.authorizationDenied
            || event.authorizationDeniedGlobally
            || event.authorizationRestricted
            || event.insufficientlyInUse
            || event.accuracyLimited
            || event.conditionUnsupported
            || event.conditionLimitExceeded
            || event.persistenceUnavailable
            || event.serviceSessionRequired
        guard !unavailable else {
            failOpenLocation(roomString("Automatic place detection is unavailable, so the location-based shield is open."))
            return
        }

        switch event.state {
        case .satisfied, .unsatisfied:
            // Region events wake a fresh location check; they do not prove precise presence.
            monitorDiagnosticActive = false
            checkPlaceArrival()
        case .unknown:
            SharedState.setInsidePlace(false, generation: state.generation)
            message = roomString("Checking your focus place.")
        case .unmonitored:
            failOpenLocation(roomString("Place detection stopped, so the location-based shield is open."))
            return
        @unknown default:
            failOpenLocation(roomString("Offzone can't confirm the place, so the location-based shield is open."))
            return
        }
        refresh()
    }

    private func synchronizeLocationAutomation() {
#if targetEnvironment(simulator)
        // Simulator location may be injected through Xcode; never fabricate an arrival.
        placeMonitoringNeedsAttention = false
#else
        guard SharedState.ruleEnabled, SharedState.hasPlace, SharedState.placeMode == .gps else {
            stopLocationAutomation()
            placeMonitoringNeedsAttention = false
            SharedState.setInsidePlace(false)
            return
        }
        let permissionReady = locationStatus == .authorizedAlways
            && locationManager.accuracyAuthorization == .fullAccuracy
        let recovered = permissionReady && !locationAutomationReady
        locationAutomationReady = permissionReady
        guard permissionReady else {
            placeMonitoringNeedsAttention = true
            SharedState.setInsidePlace(false)
            return
        }

        placeMonitoringNeedsAttention = monitorDiagnosticActive
        ensureAlwaysLocationSession()
        startPlaceMonitoringWhenDataIsAvailable()
        if recovered && placeMonitor != nil {
            startPlaceMonitoring(replacingSavedCondition: true)
        }
#endif
    }

    private func stopLocationAutomation() {
        cancelArrivalCheck()
        let wasRunning = locationAutomationReady
            || locationServiceSession != nil
            || placeMonitorTask != nil
        locationAutomationReady = false
        locationDiagnosticTask?.cancel()
        locationDiagnosticTask = nil
        locationServiceSession?.invalidate()
        locationServiceSession = nil
        placeMonitorTask?.cancel()
        placeMonitorTask = nil
        let oldIdentifier = monitoredPlaceIdentifier
        monitoredPlaceIdentifier = nil
        guard wasRunning, let placeMonitor, let oldIdentifier else { return }
        Task { await placeMonitor.remove(oldIdentifier) }
    }

    private func ensureAlwaysLocationSession() {
        guard locationServiceSession == nil else { return }
        let session = CLServiceSession(authorization: .always)
        locationServiceSession = session
        locationDiagnosticTask = Task { [weak self] in
            do {
                for try await diagnostic in session.diagnostics {
                    guard !Task.isCancelled else { return }
                    if diagnostic.authorizationRequestInProgress {
                        self?.failOpenLocation(roomString("Waiting for your Location choice."))
                    } else if diagnostic.authorizationDenied
                        || diagnostic.authorizationDeniedGlobally
                        || diagnostic.authorizationRestricted
                        || diagnostic.insufficientlyInUse
                        || diagnostic.fullAccuracyDenied
                        || diagnostic.alwaysAuthorizationDenied
                        || diagnostic.serviceSessionRequired {
                        self?.failOpenLocation(roomString("Offzone can't confirm Location access, so the shield is open."))
                    } else {
                        self?.monitorDiagnosticActive = false
                        self?.placeMonitoringNeedsAttention = false
                        self?.startPlaceMonitoring(replacingSavedCondition: true)
                    }
                }
            } catch is CancellationError {
                return
            } catch {
                self?.failOpenLocation(roomString("Offzone can't confirm Location Services, so the shield is open."))
            }
        }
    }

    private func startPlaceMonitoringWhenDataIsAvailable() {
        guard !UIApplication.shared.isProtectedDataAvailable else {
            protectedDataTask?.cancel()
            protectedDataTask = nil
            startPlaceMonitoring()
            return
        }
        guard protectedDataTask == nil else { return }
        protectedDataTask = Task { [weak self] in
            if UIApplication.shared.isProtectedDataAvailable {
                self?.protectedDataTask = nil
                self?.refresh()
                return
            }
            for await _ in NotificationCenter.default.notifications(
                named: UIApplication.protectedDataDidBecomeAvailableNotification
            ) {
                guard !Task.isCancelled else { return }
                self?.protectedDataTask = nil
                self?.refresh()
                return
            }
        }
    }

    private func failOpenLocation(_ reason: String) {
        cancelArrivalCheck()
        monitorDiagnosticActive = true
        placeMonitoringNeedsAttention = true
        SharedState.setInsidePlace(false)
        SharedState.reconcileRestrictions()
        isFocused = false
        reportError(reason)
    }

    private func armScheduleBoundaryRefresh() {
        scheduleRefreshTask?.cancel()
        guard SharedState.ruleEnabled,
              let schedule = DeviceActivityCenter().schedule(for: .roomDNS),
              let interval = schedule.nextInterval
        else { return }

        let boundary = interval.contains(.now) ? interval.end : interval.start
        let delay = max(0, boundary.timeIntervalSinceNow) + 0.25
        scheduleRefreshTask = Task { [weak self] in
            do {
                try await Task.sleep(for: .seconds(delay))
            } catch {
                return
            }
            guard !Task.isCancelled else { return }
            self?.refresh()
        }
    }

    private static func minutes(from date: Date) -> Int {
        let components = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (components.hour ?? 0) * 60 + (components.minute ?? 0)
    }

    private static func date(minutes: Int) -> Date {
        Calendar.current.date(
            byAdding: .minute,
            value: minutes,
            to: Calendar.current.startOfDay(for: .now)
        ) ?? .now
    }
}

extension AppModel: CLLocationManagerDelegate {
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor [weak self] in
            self?.handleAuthorizationChange(status)
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        Task { @MainActor [weak self] in
            guard let self else { return }
            if self.captureCurrentLocation { self.savePlace(location) }
            else { self.updatePlaceArrival(location) }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor [weak self] in
            guard let self else { return }
            if self.isCheckingPlace {
                self.cancelArrivalCheck()
                self.failOpenLocation(roomString("Location could not be confirmed. Check again near a window or outside."))
            } else if self.captureCurrentLocation {
                self.cancelPlaceLookup()
                self.reportError(roomString("Couldn't get your current location: %@", error.localizedDescription))
                self.refresh()
            }
        }
    }
}
