import Foundation

enum FocusGoal: String, CaseIterable, Identifiable {
    case work, rest, presence, personal
    var id: String { rawValue }
    var title: String {
        switch self {
        case .work: "Work"
        case .rest: "Rest"
        case .presence: "Presence"
        case .personal: "Personal time"
        }
    }
    var headline: String {
        switch self {
        case .work: "Make room for your focus."
        case .rest: "Make room for calmer evenings."
        case .presence: "Make room for being here."
        case .personal: "Make room for yourself."
        }
    }
    var suggestedWindow: FocusWindow { self == .work ? .morning : .evening }
}

enum FocusWindow: String, CaseIterable, Identifiable {
    case morning, afternoon, evening
    var id: String { rawValue }
    var startMinutes: Int {
        switch self {
        case .morning: 9 * 60
        case .afternoon: 14 * 60
        case .evening: 20 * 60
        }
    }
    var endMinutes: Int { startMinutes + 60 }
    func date(minutes: Int, now: Date = .now, calendar: Calendar = .current) -> Date {
        calendar.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: now) ?? now
    }
}

enum OnboardingProfile {
    static let completedKey = "onboarding.completed"
    static let goalKey = "onboarding.goal"

    static func isCompleted(defaults: UserDefaults = .standard) -> Bool {
        defaults.bool(forKey: completedKey)
    }

    static func draftMinutes(windowRaw: String, goal: FocusGoal, customStartMinutes: Int) -> (start: Int, end: Int) {
        if windowRaw == "custom" {
            return (customStartMinutes, (customStartMinutes + 60) % (24 * 60))
        }
        let window = FocusWindow(rawValue: windowRaw) ?? goal.suggestedWindow
        return (window.startMinutes, window.endMinutes)
    }

    static func shouldShowWelcome(hasRules: Bool, storageError: Bool, completed: Bool) -> Bool {
        !hasRules && !storageError && !completed
    }
}

/// Schedules Apple's native StoreKit review prompt only after positive milestones (never on errors or early exits).
enum ReviewPromptPolicy {
    static let momentsKey = "review.positiveMoments"
    static let lastPromptedAtKey = "review.lastPromptedAt"
    static let lastPromptedMomentsKey = "review.lastPromptedMoments"
    static let firstPromptMoments = 2
    static let repeatPromptDelta = 3
    static let cooldownSeconds: TimeInterval = 60 * 86_400

    static func shouldPrompt(
        momentsCount: Int,
        lastPromptedAt: Date?,
        lastPromptedMoments: Int,
        now: Date = .now
    ) -> Bool {
        guard momentsCount >= firstPromptMoments else { return false }
        guard let lastPromptedAt else { return true }
        guard now.timeIntervalSince(lastPromptedAt) >= cooldownSeconds else { return false }
        return momentsCount - lastPromptedMoments >= repeatPromptDelta
    }

    @discardableResult
    static func recordPositiveMoment(defaults: UserDefaults = .standard, now: Date = .now) -> Bool {
        let moments = defaults.integer(forKey: momentsKey) + 1
        defaults.set(moments, forKey: momentsKey)
        let lastInterval = defaults.double(forKey: lastPromptedAtKey)
        let lastPromptedAt = lastInterval > 0 ? Date(timeIntervalSince1970: lastInterval) : nil
        let lastPromptedMoments = defaults.integer(forKey: lastPromptedMomentsKey)
        guard shouldPrompt(
            momentsCount: moments,
            lastPromptedAt: lastPromptedAt,
            lastPromptedMoments: lastPromptedMoments,
            now: now
        ) else {
            return false
        }
        defaults.set(now.timeIntervalSince1970, forKey: lastPromptedAtKey)
        defaults.set(moments, forKey: lastPromptedMomentsKey)
        return true
    }
}

/// Grants 14 days of full free starter access from first launch before transitioning to Offzone Pro.
enum StarterAccessPolicy {
    static let firstLaunchKey = "starter.firstLaunchAt"
    static let durationDays = 14
    static let durationSeconds: TimeInterval = TimeInterval(durationDays) * 86_400
    static let reminderBannerThresholdDays = 3

    @discardableResult
    static func ensureStarted(defaults: UserDefaults = .standard, now: Date = .now) -> Date {
        let existing = defaults.double(forKey: firstLaunchKey)
        if existing > 0 {
            return Date(timeIntervalSince1970: existing)
        }
        defaults.set(now.timeIntervalSince1970, forKey: firstLaunchKey)
        return now
    }

    static func remainingDays(firstLaunchAt: Date, now: Date = .now) -> Int {
        let elapsed = max(0, now.timeIntervalSince(firstLaunchAt))
        guard elapsed < durationSeconds else { return 0 }
        return min(durationDays, Int(ceil((durationSeconds - elapsed) / 86_400)))
    }

    static func isActive(firstLaunchAt: Date, now: Date = .now) -> Bool {
        remainingDays(firstLaunchAt: firstLaunchAt, now: now) > 0
    }

    static func remainingDays(defaults: UserDefaults = .standard, now: Date = .now) -> Int {
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-offzoneStarterExpired") {
            return 0
        }
#endif
        let startedAt = ensureStarted(defaults: defaults, now: now)
        return remainingDays(firstLaunchAt: startedAt, now: now)
    }

    static func isActive(defaults: UserDefaults = .standard, now: Date = .now) -> Bool {
        remainingDays(defaults: defaults, now: now) > 0
    }

    static func showsExpiryReminderBanner(hasProAccess: Bool, remainingDays: Int) -> Bool {
        !hasProAccess && remainingDays > 0 && remainingDays <= reminderBannerThresholdDays
    }
}

enum FeedbackCategory: String, CaseIterable, Identifiable {
    case bug
    case feature
    case general

    var id: String { rawValue }

    var titleKey: String {
        switch self {
        case .bug: return "feedback_category_bug"
        case .feature: return "feedback_category_idea"
        case .general: return "feedback_category_general"
        }
    }
}

enum FeedbackValidation {
    static let minMessageLength = 3
    static let maxMessageLength = 2000
    static let maxContactLength = 254

    static func sanitize(message: String, contact: String) -> (message: String, contact: String?)? {
        let cleanMessage = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanMessage.count >= minMessageLength, cleanMessage.count <= maxMessageLength else { return nil }
        let cleanContact = contact.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanContact.count <= maxContactLength else { return nil }
        return (cleanMessage, cleanContact.isEmpty ? nil : cleanContact)
    }
}
