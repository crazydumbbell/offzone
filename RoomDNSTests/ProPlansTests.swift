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

    func testProAccessGateGatesFocusActionsButNeverGatesActiveSessionRestore() {
        XCTAssertFalse(ProAccessGate.canActivateRule(hasProAccess: false))
        XCTAssertTrue(ProAccessGate.canActivateRule(hasProAccess: true))

        XCTAssertFalse(ProAccessGate.canStartFocus(hasProAccess: false))
        XCTAssertTrue(ProAccessGate.canStartFocus(hasProAccess: true))

        XCTAssertFalse(ProAccessGate.canQuickFocus(hasProAccess: false))
        XCTAssertTrue(ProAccessGate.canQuickFocus(hasProAccess: true))

        // Safety invariant: active session restore must never depend on Pro access.
        XCTAssertFalse(ProAccessGate.canRestoreActiveSession(hasActiveSession: false))
        XCTAssertTrue(ProAccessGate.canRestoreActiveSession(hasActiveSession: true))
        XCTAssertFalse(HardPaywallPolicy.showsPaywallRestoreAccess(hasActiveSession: false))
        XCTAssertTrue(HardPaywallPolicy.showsPaywallRestoreAccess(hasActiveSession: true))
    }

    func testHardPaywallFlowBlocksNonProAndRoutesUnlockedUsers() {
        // Fresh user without Pro starts on Welcome, then enters Paywall on Get started.
        XCTAssertEqual(
            HardPaywallPolicy.rootGate(hasProAccess: false, shouldShowWelcome: true, welcomeDismissed: false),
            .welcome
        )
        XCTAssertEqual(
            HardPaywallPolicy.rootGate(hasProAccess: false, shouldShowWelcome: true, welcomeDismissed: true),
            .paywall
        )

        // Existing user without Pro is covered immediately by Paywall.
        XCTAssertEqual(
            HardPaywallPolicy.rootGate(hasProAccess: false, shouldShowWelcome: false, welcomeDismissed: false),
            .paywall
        )

        // Pro access unlocks the app unless holding the purchase confirmation or previewing the paywall.
        XCTAssertEqual(
            HardPaywallPolicy.rootGate(hasProAccess: true, shouldShowWelcome: true, welcomeDismissed: false),
            .unlocked
        )
        XCTAssertEqual(
            HardPaywallPolicy.rootGate(hasProAccess: true, shouldShowWelcome: false, welcomeDismissed: true, holdingPaywallDone: true),
            .paywall
        )
        XCTAssertEqual(
            HardPaywallPolicy.rootGate(hasProAccess: true, shouldShowWelcome: false, welcomeDismissed: false, previewPaywall: true),
            .paywall
        )

        // Post-unlock routing: fresh users enter setup (permission -> targets), existing users enter Home.
        XCTAssertEqual(
            HardPaywallPolicy.postUnlockStep(onboardingCompleted: false, hasRules: false, storageError: false, screenTimeAuthorized: false),
            .permission
        )
        XCTAssertEqual(
            HardPaywallPolicy.postUnlockStep(onboardingCompleted: false, hasRules: false, storageError: false, screenTimeAuthorized: true),
            .targets
        )
        XCTAssertNil(
            HardPaywallPolicy.postUnlockStep(onboardingCompleted: true, hasRules: false, storageError: false, screenTimeAuthorized: false)
        )
        XCTAssertNil(
            HardPaywallPolicy.postUnlockStep(onboardingCompleted: false, hasRules: true, storageError: false, screenTimeAuthorized: false)
        )
        XCTAssertNil(
            HardPaywallPolicy.postUnlockStep(onboardingCompleted: false, hasRules: false, storageError: true, screenTimeAuthorized: false)
        )
    }

    func testPaywallLegalNoticeCombinesParagraphsVerbatimIntoSingleBlock() {
        let storage = "Stored only on this iPhone, excluded from backup."
        let legal = "Cancel anytime in your App Store subscriptions. Renews automatically until cancelled."
        let combined = ProPlansLegalNotice.combined(storage: storage, intro: nil, legal: legal)
        XCTAssertEqual(combined, "\(storage) \(legal)")
        XCTAssertFalse(combined.contains("\n"))

        let withIntro = ProPlansLegalNotice.combined(storage: storage, intro: "Intro offer.", legal: legal)
        XCTAssertEqual(withIntro, "\(storage) Intro offer. \(legal)")
    }
}
