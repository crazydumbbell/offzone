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
