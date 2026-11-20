import XCTest
import RevenueCat
@testable import RoomDNS

final class ProPlansTests: XCTestCase {
    private func plan(_ amount: String, annual: Bool = false, currency: String? = "USD", trial: Int? = nil) -> ProPlanDisplay {
        .init(id: annual ? "annual" : "monthly", annual: annual, price: Decimal(string: amount)!,
              priceText: amount, currency: currency, trialDays: trial)
    }

    func testOnlyEligibleFreeTrialsArePromised() {
        XCTAssertTrue(ProPlanMath.canUseFreeTrial(eligibility: .eligible, mode: .freeTrial))
        for status: IntroEligibilityStatus? in [nil, .unknown, .ineligible] {
            XCTAssertFalse(ProPlanMath.canUseFreeTrial(eligibility: status, mode: .freeTrial))
        }
        XCTAssertFalse(ProPlanMath.canUseFreeTrial(eligibility: .eligible, mode: .payAsYouGo))
        XCTAssertFalse(ProPlanMath.canUseFreeTrial(eligibility: .eligible, mode: .payUpFront))
        XCTAssertFalse(ProPlanMath.canUseFreeTrial(eligibility: .eligible, mode: nil))
    }

    func testSavingsRoundDownAndOnlyCompareMatchingCurrencies() {
        XCTAssertEqual(ProPlanMath.savingsPercent(annual: plan("29.99", annual: true), monthly: plan("4.99")), 49)
        XCTAssertEqual(ProPlanMath.savingsPercent(annual: plan("50.001", annual: true), monthly: plan("10")), 58)
        XCTAssertNil(ProPlanMath.savingsPercent(annual: plan("60", annual: true), monthly: plan("4.99")))
        XCTAssertNil(ProPlanMath.savingsPercent(annual: plan("59.88", annual: true), monthly: plan("4.99")))
        XCTAssertNil(ProPlanMath.savingsPercent(annual: plan("29.99", annual: true, currency: "EUR"), monthly: plan("4.99")))
        XCTAssertNil(ProPlanMath.savingsPercent(annual: plan("29.99", annual: true, currency: nil), monthly: plan("4.99")))
        XCTAssertNil(ProPlanMath.savingsPercent(annual: plan("29.99", annual: true), monthly: plan("0")))
        XCTAssertNil(ProPlanMath.savingsPercent(annual: nil, monthly: plan("4.99")))
    }

    func testMonthlyEquivalentRetainsDecimalPrecisionAndFormatsCurrency() throws {
        let annual = plan("29.99", annual: true)
        let amount = try XCTUnwrap(ProPlanMath.monthlyEquivalent(annual: annual, monthly: plan("4.99")))
        XCTAssertEqual(amount, Decimal(string: "29.99")! / 12)
        XCTAssertEqual(ProPlanMath.money(amount, currency: "USD", locale: Locale(identifier: "en_US")), "$2.50")
        XCTAssertEqual(ProPlanMath.money(0, currency: "JPY", locale: Locale(identifier: "ja_JP")), "¥0")
        XCTAssertNil(ProPlanMath.money(amount, currency: nil))
        XCTAssertNil(ProPlanMath.monthlyEquivalent(annual: annual, monthly: plan("4.99", currency: "EUR")))
        XCTAssertNil(ProPlanMath.monthlyEquivalent(annual: annual, monthly: nil))
    }

    func testDefaultPlanPrefersMonthlyTrialThenAnyTrialThenAnnual() {
        let monthly = plan("4.99", trial: 14)
        let annual = plan("29.99", annual: true)
        XCTAssertEqual(ProPlanMath.defaultPlan([annual, monthly])?.id, monthly.id)
        XCTAssertEqual(ProPlanMath.defaultPlan([annual, monthly])?.trialDays, 14)
        XCTAssertEqual(ProPlanMath.defaultPlan([plan("29.99", annual: true, trial: 7), monthly])?.id, monthly.id)
        XCTAssertEqual(ProPlanMath.defaultPlan([plan("4.99"), plan("29.99", annual: true, trial: 7)])?.trialDays, 7)
        XCTAssertEqual(ProPlanMath.defaultPlan([plan("4.99"), annual])?.id, annual.id)
        XCTAssertEqual(ProPlanMath.defaultPlan([monthly])?.id, monthly.id)
        XCTAssertNil(ProPlanMath.defaultPlan([]))
    }

    func testCardOrderPlacesDefaultFirstAndPreservesOtherOrder() {
        let monthly = plan("4.99", trial: 14)
        let annual = plan("29.99", annual: true)
        XCTAssertEqual(ProPlanMath.ordered([annual, monthly]).map(\.id), [monthly.id, annual.id])
        XCTAssertEqual(ProPlanMath.ordered([monthly, annual]).map(\.id), [monthly.id, annual.id])
        XCTAssertEqual(ProPlanMath.ordered([plan("4.99"), annual]).map(\.id), [annual.id, monthly.id])
        XCTAssertTrue(ProPlanMath.ordered([]).isEmpty)
    }

    func testStoreActionsRequireVerifiedAccountBeforePurchaseOrRestore() {
        XCTAssertEqual(ProPlanMath.storeAction(verifiedAccount: false), .signIn)
        XCTAssertEqual(ProPlanMath.storeAction(verifiedAccount: false, restoring: true), .signIn)
        XCTAssertEqual(ProPlanMath.storeAction(verifiedAccount: true), .purchase)
        XCTAssertEqual(ProPlanMath.storeAction(verifiedAccount: true, restoring: true), .restore)
    }

    func testStorePeriodsAndTimelineHandleShortTrials() {
        XCTAssertEqual(ProPlanMath.days(.init(value: 14, unit: .day)), 14)
        XCTAssertEqual(ProPlanMath.days(.init(value: 2, unit: .week)), 14)
        XCTAssertEqual(ProPlanMath.days(.init(value: 1, unit: .week)), 7)
        XCTAssertEqual(ProPlanMath.days(.init(value: 1, unit: .month), count: 2), 60)
        XCTAssertNil(ProPlanMath.days(.init(value: 1, unit: .day), count: 0))
        XCTAssertNil(ProPlanMath.days(.init(value: Int.max, unit: .week)))
        XCTAssertEqual(ProPlanMath.timelineDays(14), [0, 13, 14])
        XCTAssertEqual(ProPlanMath.timelineDays(3), [0, 2, 3])
        XCTAssertEqual(ProPlanMath.timelineDays(1), [0, 1])
        XCTAssertEqual(ProPlanMath.timelineDays(0), [])
    }

    func testReminderIsExactly24HoursBeforeConfirmedExpiry() {
        let now = Date(timeIntervalSince1970: 1000)
        let end = now.addingTimeInterval(14 * 86_400)
        XCTAssertEqual(ProPlanMath.reminderDate(trialEndsAt: end, now: now), now.addingTimeInterval(13 * 86_400))
        XCTAssertNil(ProPlanMath.reminderDate(trialEndsAt: now.addingTimeInterval(86_400), now: now))
        XCTAssertNil(ProPlanMath.reminderDate(trialEndsAt: now.addingTimeInterval(86_399), now: now))
        XCTAssertNil(ProPlanMath.reminderDate(trialEndsAt: now, now: now))
    }
}
