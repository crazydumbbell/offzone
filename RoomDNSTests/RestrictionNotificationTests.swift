import XCTest
@testable import RoomDNS

final class RestrictionNotificationTests: XCTestCase {
    func testObservedTransitionsAndDeduplication() throws {
        let id = UUID(), generation = UUID()
        let unknown = RestrictionNotificationSnapshot(ruleID: id, ruleName: "Desk", generation: generation,
                                                      insidePlace: nil, shielding: false)
        var outside = unknown
        outside.insidePlace = false
        XCTAssertEqual(outside.events(since: nil), [])
        XCTAssertEqual(outside.events(since: unknown), [])
        var inside = outside
        inside.insidePlace = true
        XCTAssertEqual(inside.events(since: outside), [.entered("Desk")])
        var blocking = inside
        blocking.shielding = true
        XCTAssertEqual(blocking.events(since: outside), [.entered("Desk"), .started("Desk")])
        XCTAssertEqual(blocking.events(since: blocking), [])
        XCTAssertEqual(outside.events(since: blocking), [.left("Desk"), .ended("Desk")])
        XCTAssertEqual(inside.events(since: blocking), [.ended("Desk")]) // Scheduled end / recovery.
        var paused = unknown
        paused.generation = UUID()
        XCTAssertEqual(paused.events(since: blocking), [.ended("Desk")]) // No invented zone exit.
        let decoded = try JSONDecoder().decode(RestrictionNotificationSnapshot.self, from: JSONEncoder().encode(blocking))
        XCTAssertEqual(blocking.events(since: decoded), []) // Cross-process/relaunch checkpoint.
        var replacement = blocking
        replacement.ruleID = UUID()
        replacement.ruleName = "Home"
        replacement.generation = UUID()
        XCTAssertEqual(replacement.events(since: blocking), [.entered("Home"), .ended("Desk"), .started("Home")])
    }

    func testExistingRuntimeDecodesWithoutNotificationFields() throws {
        let original = RuntimeState()
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(original)) as? [String: Any])
        json.removeValue(forKey: "notificationSnapshot")
        json.removeValue(forKey: "confirmedInsidePlace")
        json.removeValue(forKey: "placeObservedAt")
        json.removeValue(forKey: "placeFocusOccurrenceStart")
        let decoded = try JSONDecoder().decode(RuntimeState.self, from: JSONSerialization.data(withJSONObject: json))
        XCTAssertEqual(decoded.generation, original.generation)
        XCTAssertNil(decoded.notificationSnapshot)
        XCTAssertNil(decoded.confirmedInsidePlace)
        XCTAssertNil(decoded.placeObservedAt)
        XCTAssertNil(decoded.placeFocusOccurrenceStart)
        XCTAssertFalse(decoded.placeFocusConfirmed(at: .now))
    }
}
