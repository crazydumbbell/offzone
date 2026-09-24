import Foundation

struct ProJournalData: Codable, Equatable {
    struct Plan: Codable, Equatable {
        var intention: String
        var context: String
        var weekdays: Set<Int>
    }
    struct Reflection: Codable, Equatable {
        var note: String
        var outcome: String
    }
    var version = 1
    var plans: [String: Plan] = [:]
    var reflections: [String: Reflection] = [:]
}

/// A device-local journal. Failed loads cannot be overwritten by later saves.
struct ProJournalStore {
    enum Failure: Error { case invalidData, accessRequired }
    let url: URL
    private(set) var data: ProJournalData
    private(set) var loadFailed = false
    static let contexts = ["work", "rest", "presence", "personal"]
    static let outcomes = ["Kept my intention", "Partly", "Try again"]
    static var calendar: Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = .current
        return calendar
    }
    static func key(_ date: Date, calendar: Calendar = calendar) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }
    static func week(_ date: Date, calendar: Calendar = calendar) -> Date {
        calendar.dateInterval(of: .weekOfYear, for: date)!.start
    }
    static var defaultURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("OffzoneJournal/journal.json")
    }
    init(url: URL = defaultURL) {
        self.url = url
        data = ProJournalData()
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        do {
            let decoded = try JSONDecoder().decode(ProJournalData.self, from: Data(contentsOf: url))
            try Self.validate(decoded)
            data = decoded
        } catch { loadFailed = true }
    }
    static func validate(_ data: ProJournalData) throws {
        guard data.version == 1,
              data.plans.keys.allSatisfy(validDay), data.reflections.keys.allSatisfy(validDay),
              data.plans.values.allSatisfy({ !$0.intention.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.intention.count <= 160 && contexts.contains($0.context) && !$0.weekdays.isEmpty && $0.weekdays.isSubset(of: Set(1...7)) }),
              data.reflections.values.allSatisfy({ !$0.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.note.count <= 2000 && outcomes.contains($0.outcome) }) else { throw Failure.invalidData }
    }
    private static func validDay(_ value: String) -> Bool {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.isLenient = false
        return formatter.date(from: value).map { key($0) == value } ?? false
    }
    mutating func setPlan(_ plan: ProJournalData.Plan, week: Date, hasAccess: Bool) throws {
        guard hasAccess else { throw Failure.accessRequired }
        var next = data
        next.plans[Self.key(Self.week(week))] = plan
        try save(next)
    }
    mutating func reflect(_ reflection: ProJournalData.Reflection, date: Date, hasAccess: Bool) throws {
        guard hasAccess else { throw Failure.accessRequired }
        guard Self.calendar.startOfDay(for: date) <= Self.calendar.startOfDay(for: .now) else { throw Failure.invalidData }
        var next = data
        next.reflections[Self.key(date)] = reflection
        try save(next)
    }
    mutating func deleteReflection(_ key: String) throws {
        var next = data; next.reflections.removeValue(forKey: key); try save(next)
    }
    mutating func deletePlan(week: Date) throws {
        var next = data; next.plans.removeValue(forKey: Self.key(Self.week(week)) ); try save(next)
    }
    mutating func deleteAll() throws {
        if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
        data = ProJournalData(); loadFailed = false
    }
    func export() throws -> String {
        let bytes = loadFailed ? try Data(contentsOf: url) : try JSONEncoder().encode(data)
        guard let text = String(data: bytes, encoding: .utf8) else { throw Failure.invalidData }
        return text
    }
    func summary(week: Date) -> (planned: Int, reflected: Int, kept: Int) {
        let start = Self.week(week)
        let keys = (0..<7).map { Self.key(Self.calendar.date(byAdding: .day, value: $0, to: start)!) }
        let entries = keys.compactMap { data.reflections[$0] }
        return (data.plans[Self.key(start)]?.weekdays.count ?? 0, entries.count,
                entries.filter { $0.outcome == Self.outcomes[0] }.count)
    }
    private mutating func save(_ next: ProJournalData) throws {
        guard !loadFailed else { throw Failure.invalidData }
        try Self.validate(next)
        var directory = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try directory.setResourceValues(values)
        #if os(iOS)
        try JSONEncoder().encode(next).write(to: url, options: [.atomic, .completeFileProtection])
        #else
        try JSONEncoder().encode(next).write(to: url, options: .atomic)
        #endif
        data = next
    }
}
