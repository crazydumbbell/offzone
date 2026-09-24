// Run: swiftc RoomDNS/App/ProJournal.swift tools/check_pro_journal.swift -o /tmp/check-pro-journal && /tmp/check-pro-journal
import Foundation

@main struct JournalCheck {
    static func main() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appendingPathComponent("journal.json")
        var store = ProJournalStore(url: url)
        let date = ISO8601DateFormatter().date(from: "2025-12-31T12:00:00Z")!
        let plan = ProJournalData.Plan(intention: "Read with my phone away", context: "personal", weekdays: [2, 4, 6])
        try store.setPlan(plan, week: date, hasAccess: true)
        try store.reflect(.init(note: "Read a chapter.", outcome: "Kept my intention"), date: date, hasAccess: true)
        let saved = store.data
        let reloaded = ProJournalStore(url: url)
        precondition(reloaded.data == saved && !reloaded.loadFailed)
        let summary = store.summary(week: date)
        precondition(summary.planned == 3 && summary.reflected == 1 && summary.kept == 1)
        let nextWeek = ProJournalStore.calendar.date(byAdding: .day, value: 7, to: date)!
        precondition(store.summary(week: nextWeek).reflected == 0)
        do { try store.setPlan(plan, week: date, hasAccess: false); fatalError("Expired access wrote data") }
        catch ProJournalStore.Failure.accessRequired {}
        do { try store.reflect(.init(note: "", outcome: "Kept my intention"), date: date, hasAccess: true); fatalError("Empty reflection accepted") }
        catch ProJournalStore.Failure.invalidData {}
        precondition(store.data == saved)
        // Access expiry leaves export and explicit deletion available.
        let exported = try store.export(); precondition(exported.contains("Read a chapter."))
        try store.deleteReflection(ProJournalStore.key(date))
        precondition(ProJournalStore(url: url).data.reflections.isEmpty)
        let corrupt = Data("{damaged journal".utf8)
        try corrupt.write(to: url)
        var damaged = ProJournalStore(url: url)
        precondition(damaged.loadFailed)
        do { try damaged.setPlan(plan, week: date, hasAccess: true); fatalError("Corrupt data overwritten") }
        catch ProJournalStore.Failure.invalidData {}
        let preserved = try Data(contentsOf: url); precondition(preserved == corrupt)
        try damaged.deleteAll()
        try damaged.setPlan(plan, week: date, hasAccess: true)
        precondition(!damaged.loadFailed && ProJournalStore(url: url).data.plans.count == 1)
        print("PASS: journal persistence, weekly boundaries, access expiry, validation, corruption preservation and explicit reset")
    }
}
