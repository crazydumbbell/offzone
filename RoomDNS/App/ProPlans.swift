import Foundation
import RevenueCat
import UserNotifications

/// The same store-derived values drive both the introductory promise and the purchase step.
struct ProPlanDisplay: Identifiable {
    let id: String
    let annual: Bool
    let price: Decimal
    let priceText: String
    let currency: String?
    let trialDays: Int?
    let package: Package?
    var cadence: String { roomString(annual ? "pro_per_year" : "pro_per_month", priceText) }

    init(package: Package, eligibility: IntroEligibilityStatus?) {
        let product = package.storeProduct
        id = package.identifier
        annual = package.packageType == .annual
        price = product.price
        priceText = product.localizedPriceString
        currency = product.currencyCode
        self.package = package
        if let intro = product.introductoryDiscount,
           ProPlanMath.canUseFreeTrial(eligibility: eligibility, mode: intro.paymentMode) {
            trialDays = ProPlanMath.days(intro.subscriptionPeriod, count: intro.numberOfPeriods)
        } else { trialDays = nil }
    }

    init(id: String, annual: Bool, price: Decimal, priceText: String, currency: String?, trialDays: Int?) {
        self.id = id; self.annual = annual; self.price = price; self.priceText = priceText
        self.currency = currency; self.trialDays = trialDays; package = nil
    }
}

enum ProPlanMath {
    static func canUseFreeTrial(eligibility: IntroEligibilityStatus?, mode: StoreProductDiscount.PaymentMode?) -> Bool {
        eligibility == .eligible && mode == .freeTrial
    }
    static func defaultPlan(_ plans: [ProPlanDisplay]) -> ProPlanDisplay? {
        plans.first { !$0.annual && ($0.trialDays ?? 0) > 0 } ??
        plans.first { ($0.trialDays ?? 0) > 0 } ??
        plans.first(where: \.annual) ?? plans.first
    }

    static func ordered(_ plans: [ProPlanDisplay]) -> [ProPlanDisplay] {
        guard let first = defaultPlan(plans) else { return [] }
        return [first] + plans.filter { $0.id != first.id }
    }

    static func storeAction(verifiedAccount: Bool, restoring: Bool = false) -> ProStoreAction {
        verifiedAccount ? (restoring ? .restore : .purchase) : .signIn
    }

    static func days(_ period: SubscriptionPeriod, count: Int = 1) -> Int? {
        let multiplier: Int
        switch period.unit {
        case .day: multiplier = 1
        case .week: multiplier = 7
        case .month: multiplier = 30
        case .year: multiplier = 365
        @unknown default: return nil
        }
        let (periods, overflow) = period.value.multipliedReportingOverflow(by: count)
        let (days, daysOverflow) = periods.multipliedReportingOverflow(by: multiplier)
        return !overflow && !daysOverflow && count > 0 && days > 0 ? days : nil
    }

    static func sameCurrency(_ annual: ProPlanDisplay?, _ monthly: ProPlanDisplay?) -> Bool {
        guard let annual, annual.annual, let monthly, !monthly.annual,
              let currency = annual.currency, !currency.isEmpty else { return false }
        return currency == monthly.currency
    }

    static func savingsPercent(annual: ProPlanDisplay?, monthly: ProPlanDisplay?) -> Int? {
        guard sameCurrency(annual, monthly), let annual, let monthly,
              annual.price >= 0, monthly.price > 0, annual.price < monthly.price * 12 else { return nil }
        var value = (monthly.price * 12 - annual.price) * 100 / (monthly.price * 12)
        var floor = Decimal()
        NSDecimalRound(&floor, &value, 0, .down)
        return NSDecimalNumber(decimal: floor).intValue
    }

    static func monthlyEquivalent(annual: ProPlanDisplay?, monthly: ProPlanDisplay?) -> Decimal? {
        guard sameCurrency(annual, monthly), let annual, annual.price >= 0 else { return nil }
        return annual.price / 12
    }

    static func money(_ amount: Decimal, currency: String?, locale: Locale = .current) -> String? {
        guard let currency, Locale.commonISOCurrencyCodes.contains(currency) else { return nil }
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = locale
        formatter.currencyCode = currency
        return formatter.string(from: NSDecimalNumber(decimal: amount))
    }

    static func timelineDays(_ trialDays: Int) -> [Int] {
        guard trialDays > 0 else { return [] }
        return trialDays > 1 ? [0, trialDays - 1, trialDays] : [0, trialDays]
    }

    /// Use the confirmed trial expiry, rather than guessing from the time the button was tapped.
    static func reminderDate(trialEndsAt: Date, now: Date) -> Date? {
        let date = trialEndsAt.addingTimeInterval(-86_400)
        return date > now ? date : nil
    }

    static func dayLabel(_ days: Int) -> String {
        roomString(days == 1 ? "pro_day" : "pro_days", days)
    }

    static func futureDayLabel(_ days: Int) -> String {
        days == 1 ? roomString("pro_tomorrow") : roomString("pro_in_days", days)
    }
}

enum ProStoreAction { case signIn, purchase, restore }

struct ProPurchaseOutcome {
    let trialEndsAt: Date?
}

enum ProTrialReminder {
    static let identifier = "offzone.pro.trial-reminder"

    static func schedule(trialEndsAt: Date, now: Date = .now) async throws {
        guard let date = ProPlanMath.reminderDate(trialEndsAt: trialEndsAt, now: now) else { return }
        let center = UNUserNotificationCenter.current()
        let status = await center.notificationSettings().authorizationStatus
        guard status == .authorized || status == .provisional || status == .ephemeral else { return }
        let content = UNMutableNotificationContent()
        content.title = roomString("pro_banner_title")
        content.body = roomString("trial_reminder_body")
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, date.timeIntervalSinceNow), repeats: false)
        // A stable identifier replaces an existing request, so there is only one trial reminder.
        try await center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: trigger))
    }
}

#if DEBUG
enum ProPaywallPreview {
    static var variant: String? {
        let args = ProcessInfo.processInfo.arguments
        guard args.contains("-offzonePreviewPaywall") else { return nil }
        if let i = args.firstIndex(of: "-offzonePaywallVariant"), args.indices.contains(i + 1),
           ["plain", "signedout", "trial", "unavailable"].contains(args[i + 1]) { return args[i + 1] }
        return "trial"
    }

    static var plans: [ProPlanDisplay]? {
        guard let variant, variant != "unavailable" else { return nil }
        let trial = variant == "plain" ? nil : 14
        return ProPlanMath.ordered([
            .init(id: "preview_annual", annual: true, price: Decimal(string: "29.99")!, priceText: "$29.99", currency: "USD", trialDays: nil),
            .init(id: "preview_monthly", annual: false, price: Decimal(string: "4.99")!, priceText: "$4.99", currency: "USD", trialDays: trial)
        ])
    }

    static var initialStep: String? {
        let args = ProcessInfo.processInfo.arguments
        guard variant != nil, let i = args.firstIndex(of: "-offzonePaywallStep"), args.indices.contains(i + 1) else { return nil }
        return args[i + 1]
    }

    static var scrollTarget: String? {
        let args = ProcessInfo.processInfo.arguments
        guard variant != nil, let i = args.firstIndex(of: "-offzonePaywallScrollTo"), args.indices.contains(i + 1) else { return nil }
        return args[i + 1]
    }
}
#endif
