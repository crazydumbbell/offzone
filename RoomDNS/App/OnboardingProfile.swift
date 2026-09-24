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
    var detail: String {
        switch self {
        case .work: "Give one thing your full attention."
        case .rest: "Leave a little space before sleep."
        case .presence: "Be here for the people with you."
        case .personal: "Make time for something you enjoy."
        }
    }
    var symbol: String {
        switch self {
        case .work: "sun.max"
        case .rest: "moon"
        case .presence: "person.2"
        case .personal: "leaf"
        }
    }
    var ruleName: String {
        switch self {
        case .work: "Work time"
        case .rest: "Wind-down"
        case .presence: "Time together"
        case .personal: "My time"
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
    var title: String {
        switch self {
        case .morning: "In the morning"
        case .afternoon: "In the afternoon"
        case .evening: "In the evening"
        }
    }
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
    static let windowKey = "onboarding.window"

    static func shouldShowWelcome(hasRules: Bool, storageError: Bool, completed: Bool) -> Bool {
        !hasRules && !storageError && !completed
    }
}
