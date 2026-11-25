import SwiftUI
import RevenueCat

struct RoomPaywallView: View {
    let goal: FocusGoal
    var activeSessionTitle: String? = nil
    var activeSessionRestore: (() -> Void)? = nil
    var onHoldDone: (Bool) -> Void = { _ in }
    var onUnlocked: () -> Void = {}
    @EnvironmentObject private var account: AccountStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var accountPresented = false
    @State private var accountStartInManage = false
    @State private var proAtEntry: Bool?
    @State private var displayedPlans: [ProPlanDisplay] = []
    @State private var restored = false
    @State private var awaitingOffer = false

    private var preview: String? {
#if DEBUG
        ProPaywallPreview.variant
#else
        nil
#endif
    }
    private var plans: [ProPlanDisplay] {
#if DEBUG
        if preview == "unavailable" { return [] }
        if let sample = ProPaywallPreview.plans { return sample }
#endif
        return displayedPlans
    }
    private var signInPitch: Bool {
        preview == nil && plans.isEmpty && account.offersEnabled && !account.isSignedIn && !(proAtEntry ?? account.hasProAccess)
    }
    private var funnelAvailable: Bool {
        (preview != nil && !plans.isEmpty) ||
        (!(proAtEntry ?? account.hasProAccess) && account.offersEnabled && !plans.isEmpty)
    }

    var body: some View {
        NavigationStack {
            Group {
                if funnelAvailable {
                    ProFunnelView(
                        goal: goal,
                        plans: plans,
                        preview: preview != nil,
                        signIn: { openAccount(manage: true) },
                        openAccount: { openAccount(manage: false) },
                        onHoldDone: onHoldDone,
                        onUnlocked: onUnlocked
                    )
                } else if preview == nil && plans.isEmpty && account.offersEnabled && account.isBusy {
                    ProPage(centered: true) {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .accessibilityLabel("Please wait")
                    } footer: {
                        PaywallSecondaryActions(
                            showsSignIn: !account.isSignedIn,
                            busy: true,
                            restore: restoreFromFallback,
                            signIn: { openAccount(manage: true) }
                        )
                        PaywallUtilityLinks(
                            termsURL: account.termsURL,
                            privacyURL: account.privacyURL,
                            openAccount: { openAccount(manage: false) }
                        )
                    }
                } else if signInPitch {
                    ProPage(centered: true) {
                        ProInviteHero()
                        Text(roomString(goal.proHeadlineKey)).proHeading().proReveal(delay: 300)
                        ProBenefits().proReveal(delay: 370)
                    } footer: {
                        Button(roomString("pro_sign_in_plans")) { openAccount(manage: true) }
                            .roomPrimaryAction().accessibilityIdentifier("pro.signIn")
                        PaywallSecondaryActions(
                            showsSignIn: false,
                            busy: account.isBusy,
                            restore: restoreFromFallback,
                            signIn: { openAccount(manage: true) }
                        )
                        PaywallUtilityLinks(
                            termsURL: account.termsURL,
                            privacyURL: account.privacyURL,
                            openAccount: { openAccount(manage: false) }
                        )
                    }
                } else {
                    unavailable
                }
            }
            .background(Color.roomCanvas)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(roomString("pro_eyebrow")).font(.suit(.subheadline, weight: .bold)).tracking(1.4)
                }
            }
            .safeAreaInset(edge: .top, spacing: 0) {
                if activeSessionRestore != nil || preview != nil {
                    VStack(spacing: 8) {
                        if let activeSessionRestore {
                            PaywallActiveSessionBar(
                                title: activeSessionTitle ?? roomString("In focus"),
                                restore: activeSessionRestore
                            )
                        }
                        if preview != nil {
                            Text(roomString("pro_preview_note"))
                                .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 24).padding(.bottom, 8)
                        }
                    }
                    .background(Color.roomCanvas)
                }
            }
        }
        .font(.suit(.body)).foregroundStyle(Color.roomInk).tint(Color.roomAccent)
        .preferredColorScheme(.light)
        .sheet(isPresented: $accountPresented, onDismiss: {
            guard preview == nil else { return }
            updateDisplayPlans()
            loadMissingOffer()
        }) {
            RoomAccountView(goal: goal, startInManage: accountStartInManage).presentationCornerRadius(24)
        }
        .onAppear {
            if proAtEntry == nil { proAtEntry = account.hasProAccess }
            updateDisplayPlans()
#if DEBUG
            if preview != nil {
                NSLog("OFFZONE_PRO_PREVIEW variant=%@ reduceMotion=%@ dynamicType=%@", preview ?? "", String(reduceMotion), String(describing: dynamicTypeSize))
            }
#endif
        }
        .task(id: account.isSignedIn) { loadMissingOffer() }
        .onChange(of: account.canMakeStorePurchase) { _, ready in
            if ready && preview == nil && accountPresented { accountPresented = false }
        }
        .onChange(of: account.isBusy) { _, busy in
            if !busy {
                updateDisplayPlans()
                if awaitingOffer { loadMissingOffer() }
            }
        }
    }

    private func openAccount(manage: Bool) {
        accountStartInManage = manage
        accountPresented = true
    }

    private func restoreFromFallback() {
        guard account.canMakeStorePurchase else { openAccount(manage: true); return }
        Task {
            await account.restorePurchases()
            restored = account.errorMessage == nil
        }
    }

    private func updateDisplayPlans() {
        guard preview == nil, account.canShowOffer else { return }
        // Display values survive authentication; access and purchases still require the current verified UID.
        displayedPlans = ProPlanMath.ordered(account.packages.map {
            ProPlanDisplay(package: $0, eligibility: account.eligibility[$0.storeProduct.productIdentifier]?.status)
        })
    }

    private func loadMissingOffer() {
        guard preview == nil, account.offersEnabled, !account.canShowOffer else {
            awaitingOffer = false
            return
        }
        guard !account.isBusy else { awaitingOffer = true; return }
        awaitingOffer = false
        Task {
            await account.loadOfferings(forPaywall: true)
            updateDisplayPlans()
        }
    }

    // D-45 remains the sales-off / failed-offering screen. No prices or purchase claims.
    private var unavailable: some View {
        ProPage {
            RoomSpirit(state: .welcome).frame(height: 184).frame(maxWidth: .infinity).accessibilityHidden(true)
            Text(roomString(goal.headline)).proHeading()
            if !account.proBenefits.isEmpty { ProBenefits(benefits: account.proBenefits) }
            Text(account.hasProAccess ? roomString("Your Pro subscription is active.") : roomString("Plans aren’t available right now. Try again in a moment."))
                .font(.suit(.subheadline)).padding(18).frame(maxWidth: .infinity, alignment: .leading)
                .editorialPanel(Color.roomMint, outlined: false)
            if let error = account.errorMessage { Text(error).font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary) }
            if restored && !account.hasProAccess { Text("No active Pro subscription was found.").font(.suit(.subheadline)) }
        } footer: {
            if account.offersEnabled && !account.hasProAccess {
                Button("Try again") { Task { await account.loadOfferings(forPaywall: true); updateDisplayPlans() } }
                    .roomPrimaryAction().disabled(account.isBusy)
            }
            PaywallSecondaryActions(
                showsSignIn: !account.isSignedIn,
                busy: account.isBusy,
                restore: restoreFromFallback,
                signIn: { openAccount(manage: true) }
            )
            PaywallUtilityLinks(
                termsURL: account.termsURL,
                privacyURL: account.privacyURL,
                openAccount: { openAccount(manage: false) }
            )
        }
    }
}

private enum ProStep: String { case invite, remind, how, plans, done }

private struct ProFunnelView: View {
    let goal: FocusGoal
    let plans: [ProPlanDisplay]
    let preview: Bool
    let signIn: () -> Void
    let openAccount: () -> Void
    let onHoldDone: (Bool) -> Void
    let onUnlocked: () -> Void
    @EnvironmentObject private var account: AccountStore
    @EnvironmentObject private var model: AppModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var index = 0
    @State private var selectedID: String
    @State private var reminder = false
    @State private var requestingPermission = false
    @State private var done = false
    @State private var trialStarted = false
    @State private var forward = true
    @State private var notice: String?

    init(
        goal: FocusGoal,
        plans: [ProPlanDisplay],
        preview: Bool,
        signIn: @escaping () -> Void,
        openAccount: @escaping () -> Void,
        onHoldDone: @escaping (Bool) -> Void,
        onUnlocked: @escaping () -> Void
    ) {
        self.goal = goal
        self.plans = plans
        self.preview = preview
        self.signIn = signIn
        self.openAccount = openAccount
        self.onHoldDone = onHoldDone
        self.onUnlocked = onUnlocked
        _selectedID = State(initialValue: ProPlanMath.defaultPlan(plans)?.id ?? "")
#if DEBUG
        if preview, let initial = ProPaywallPreview.initialStep {
            let trial = ProPlanMath.defaultPlan(plans)?.trialDays != nil
            let steps: [ProStep] = trial ? [.invite, .remind, .how, .plans] : [.invite, .plans]
            _index = State(initialValue: steps.firstIndex(where: { $0.rawValue == initial }) ?? 0)
            _done = State(initialValue: initial == "done")
            _trialStarted = State(initialValue: initial == "done" && trial)
        }
#endif
    }

    private var defaultPlan: ProPlanDisplay { ProPlanMath.defaultPlan(plans)! }
    private var selected: ProPlanDisplay { plans.first(where: { $0.id == selectedID }) ?? defaultPlan }
    private var steps: [ProStep] { defaultPlan.trialDays != nil ? [.invite, .remind, .how, .plans] : [.invite, .plans] }
    private var step: ProStep { done ? .done : steps[min(index, steps.count - 1)] }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                if index > 0 && !done {
                    Button { go(index - 1) } label: { Image(systemName: "chevron.left").frame(width: 44, height: 44) }
                        .accessibilityLabel(roomString("Back")).accessibilityIdentifier("pro.back")
                }
                if !done {
                    HStack(spacing: 6) {
                        ForEach(steps.indices, id: \.self) { i in
                            Capsule().fill(i == index ? Color.roomAccent : Color.roomLine)
                                .frame(width: i == index ? 24 : 8, height: 8)
                        }
                    }.accessibilityElement(children: .ignore).accessibilityLabel(roomString("pro_step_of", index + 1, steps.count))
                }
                Spacer()
            }.padding(.horizontal, 24).frame(height: 44)
            ZStack {
                page.id(step).transition(transition)
            }
        }
#if DEBUG
        // Optional recording driver exercises the same step and plan state changes, without buying anything.
        .task {
            guard preview, ProcessInfo.processInfo.arguments.contains("-offzonePaywallAutoplay") else { return }
            do {
                for _ in steps.indices {
                    try await Task.sleep(for: .seconds(5))
                    if step == .plans {
                        if let monthly = plans.first(where: { !$0.annual }) {
                            withAnimation(reduceMotion ? .linear(duration: 0.1) : ProMotion.state(duration: 0.22)) { selectedID = monthly.id }
                            try await Task.sleep(for: .seconds(2))
                        }
                        withAnimation(reduceMotion ? .linear(duration: 0.1) : ProMotion.state(duration: 0.22)) { selectedID = defaultPlan.id }
                        try await Task.sleep(for: .seconds(2))
                        purchase()
                    } else { go(index + 1) } // The reminder step follows Skip, so no system permission is faked.
                }
            } catch { }
        }
#endif
    }

    private var transition: AnyTransition {
        if reduceMotion { return .opacity.animation(.linear(duration: 0.1)) }
        let sign: CGFloat = forward ? 1 : -1
        return .asymmetric(
            insertion: .modifier(active: ProShift(opacity: 0, x: 24 * sign), identity: ProShift(opacity: 1, x: 0)).animation(ProMotion.enter),
            removal: .modifier(active: ProShift(opacity: 0, x: -16 * sign), identity: ProShift(opacity: 1, x: 0)).animation(ProMotion.state(duration: 0.16)))
    }

    @ViewBuilder private var page: some View {
        switch step {
        case .invite:
            ProPage(centered: true) {
                ProInviteHero()
                Text(defaultPlan.trialDays.map { roomString("pro_try_title", ProPlanMath.dayLabel($0)) } ?? roomString(goal.proHeadlineKey))
                    .proHeading().proReveal(delay: 300)
                if defaultPlan.trialDays == nil { ProBenefits().proReveal(delay: 370) }
                if let error = account.errorMessage { Text(error).font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary) }
                if let notice { Text(notice).font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary) }
            } footer: {
                Button(roomString(defaultPlan.trialDays != nil ? "pro_start_trial" : "pro_see_plans")) { go(index + 1) }
                    .roomPrimaryAction().accessibilityIdentifier("pro.next").proReveal(delay: 440)
                PaywallSecondaryActions(
                    showsSignIn: !account.isSignedIn,
                    busy: preview ? false : account.isBusy,
                    restore: restore,
                    signIn: signIn
                )
                PaywallUtilityLinks(
                    termsURL: account.termsURL,
                    privacyURL: account.privacyURL,
                    openAccount: openAccount
                )
            }
        case .remind:
            ProPage(centered: true) {
                ProRemindHero()
                Text(roomString("pro_remind_title")).proHeading().proReveal(delay: 300)
                Text(roomString("pro_remind_body")).foregroundStyle(Color.roomInkSecondary)
                    .fixedSize(horizontal: false, vertical: true).proReveal(delay: 370)
            } footer: {
                Button(roomString("pro_remind_cta")) {
                    Task {
                        requestingPermission = true
                        reminder = (try? await AppModel.requestNotificationAuthorization()) ?? false
                        requestingPermission = false
                        go(index + 1)
                    }
                }.roomPrimaryAction().disabled(requestingPermission).accessibilityIdentifier("pro.remind")
                Button(roomString("pro_skip")) { reminder = false; go(index + 1) }
                    .frame(minHeight: 44).accessibilityIdentifier("pro.skip")
            }
        case .how:
            ProPage {
                HStack(alignment: .top, spacing: 12) {
                    Text(roomString("pro_how_title")).proHeading().frame(maxWidth: .infinity, alignment: .leading)
                    Image("OffzonePro-nook-tea").resizable().scaledToFit().frame(width: 84, height: 96)
                        .accessibilityHidden(true).proReveal(delay: 120)
                }
                ProTrialTimeline(days: ProPlanMath.timelineDays(defaultPlan.trialDays ?? 1), remind: reminder)
            } footer: {
                Button(roomString("pro_continue")) { go(index + 1) }.roomPrimaryAction().accessibilityIdentifier("pro.next")
            }
        case .plans:
            ProPlansPage(plans: plans, selected: selected, busy: preview ? false : account.isBusy,
                         signedInForPurchase: verifiedForStoreAction,
                         showsSignInAction: !account.isSignedIn,
                         showsSummary: defaultPlan.trialDays != nil,
                         select: { selectedID = $0.id }, purchase: purchase, restore: restore,
                         signIn: signIn, openAccount: openAccount, notice: notice)
        case .done:
            ProPage(centered: true) {
                ProDoneHero()
                Text(roomString("pro_done_title")).proHeading().proReveal(delay: 300)
                Text(roomString(trialStarted ? "pro_done_trial" : "pro_done_body"))
                    .foregroundStyle(Color.roomInkSecondary).fixedSize(horizontal: false, vertical: true).proReveal(delay: 370)
                if let notice { Text(notice).font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary) }
            } footer: {
                Button(roomString("pro_continue")) {
                    if !preview && account.hasProAccess {
                        model.autoArmSavedRuleIfNeeded()
                    }
                    onUnlocked()
                }.roomPrimaryAction().accessibilityIdentifier("pro.finish")
            }
        }
    }

    private func go(_ destination: Int) {
        forward = destination > index
        withAnimation(reduceMotion ? .linear(duration: 0.1) : ProMotion.enter) { index = min(max(0, destination), steps.count - 1) }
    }
    private var verifiedForStoreAction: Bool {
#if DEBUG
        if preview { return ProPaywallPreview.variant != "signedout" }
#endif
        return account.canMakeStorePurchase
    }

    private func purchase() {
        guard ProPlanMath.storeAction(verifiedAccount: verifiedForStoreAction) == .purchase else { signIn(); return }
#if DEBUG
        if preview {
            trialStarted = selected.trialDays != nil
            withAnimation(reduceMotion ? .linear(duration: 0.1) : ProMotion.enter) { done = true }
            return // No purchase, entitlement grant, or notification in a sample preview.
        }
#endif
        if account.hasProAccess {
            onHoldDone(true)
            trialStarted = false
            withAnimation(reduceMotion ? .linear(duration: 0.1) : ProMotion.enter) { done = true }
            return
        }
        let purchasedPlan = selected
        guard let package = purchasedPlan.package else { return }
        onHoldDone(true)
        Task {
            guard let result = await account.purchase(package) else {
                onHoldDone(false)
                return
            }
            trialStarted = result.trialEndsAt != nil
            withAnimation(reduceMotion ? .linear(duration: 0.1) : ProMotion.enter) { done = true }
            if reminder, let days = purchasedPlan.trialDays, days > 1, let expiry = result.trialEndsAt {
                do { try await ProTrialReminder.schedule(trialEndsAt: expiry) }
                catch { notice = roomString("Couldn't enable notifications: %@", error.localizedDescription) }
            }
        }
    }
    private func restore() {
        guard ProPlanMath.storeAction(verifiedAccount: verifiedForStoreAction, restoring: true) == .restore else { signIn(); return }
        guard !preview else { return }
        onHoldDone(true)
        Task {
            await account.restorePurchases()
            if account.hasProAccess {
                trialStarted = false
                withAnimation(reduceMotion ? .linear(duration: 0.1) : ProMotion.enter) { done = true }
            } else {
                onHoldDone(false)
                if account.errorMessage == nil { notice = roomString("No active Pro subscription was found.") }
            }
        }
    }
}

enum ProPlansLegalNotice {
    static func combined(storage: String, intro: String? = nil, legal: String) -> String {
        [storage, intro, legal]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }
}

private struct PaywallActiveSessionBar: View {
    let title: String
    let restore: () -> Void
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout(spacing: 12))
        layout {
            Label(title, systemImage: "shield.lefthalf.filled")
                .font(.suit(.subheadline, weight: .semibold))
                .foregroundStyle(Color.roomInk)
            if !dynamicTypeSize.isAccessibilitySize { Spacer(minLength: 8) }
            Button(action: restore) {
                Label(roomString("Restore access"), systemImage: "arrow.counterclockwise")
                    .font(.suit(.subheadline, weight: .bold))
                    .foregroundStyle(Color.roomCanvas)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .frame(minHeight: 44)
                    .background(Color.roomAccent, in: Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("paywall.restoreAccess")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .frame(maxWidth: 560, alignment: .leading)
        .editorialPanel(Color.roomButter, cornerRadius: 16, outlined: false)
        .padding(.horizontal, 24)
        .padding(.bottom, 4)
        .frame(maxWidth: .infinity)
    }
}

private struct PaywallSecondaryActions: View {
    let showsSignIn: Bool
    let busy: Bool
    let restore: () -> Void
    let signIn: () -> Void
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var restoreButton: some View {
        Button("Restore purchases", action: restore)
            .disabled(busy)
            .lineLimit(1)
            .frame(minHeight: 44)
            .accessibilityIdentifier("pro.restore")
    }

    private var signInButton: some View {
        Button(roomString("Already subscribed? Sign in"), action: signIn)
            .disabled(busy)
            .lineLimit(1)
            .frame(minHeight: 44)
            .accessibilityIdentifier("pro.alreadySubscribed")
    }

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 4) {
                    restoreButton
                    if showsSignIn { signInButton }
                }
                .font(.suit(.body, weight: .medium))
            } else {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 16) {
                        restoreButton
                        if showsSignIn { signInButton }
                    }
                    VStack(alignment: .leading, spacing: 0) {
                        restoreButton
                        if showsSignIn { signInButton }
                    }
                }
                .font(.suit(.subheadline, weight: .semibold))
            }
        }
        .foregroundStyle(Color.roomAccent)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct PaywallUtilityLinks: View {
    let termsURL: URL?
    let privacyURL: URL?
    let openAccount: () -> Void
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    @ViewBuilder private var termsLink: some View {
        if let termsURL {
            Link("Terms of use", destination: termsURL)
                .lineLimit(1)
                .frame(minHeight: 44)
        }
    }

    @ViewBuilder private var privacyLink: some View {
        if let privacyURL {
            Link("Privacy policy", destination: privacyURL)
                .lineLimit(1)
                .frame(minHeight: 44)
        }
    }

    private var accountButton: some View {
        Button("Account", action: openAccount)
            .lineLimit(1)
            .frame(minHeight: 44)
            .accessibilityIdentifier("pro.account")
    }

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 4) {
                    termsLink
                    privacyLink
                    accountButton
                }
                .font(.suit(.body))
            } else {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 12) { termsLink; privacyLink; accountButton }
                    VStack(alignment: .leading, spacing: 0) {
                        HStack(spacing: 12) { termsLink; privacyLink }
                        accountButton
                    }
                }
                .font(.custom(Font.suitFace(.regular), size: 13, relativeTo: .footnote))
            }
        }
        .foregroundStyle(Color.roomInkSecondary)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ProPlansPage: View {
    let plans: [ProPlanDisplay]
    let selected: ProPlanDisplay
    let busy: Bool
    let signedInForPurchase: Bool
    let showsSignInAction: Bool
    let showsSummary: Bool
    let select: (ProPlanDisplay) -> Void
    let purchase: () -> Void
    let restore: () -> Void
    let signIn: () -> Void
    let openAccount: () -> Void
    let notice: String?
    @EnvironmentObject private var account: AccountStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var burst = 0
    private var annual: ProPlanDisplay? { plans.first(where: \.annual) }
    private var monthly: ProPlanDisplay? { plans.first { !$0.annual } }

    private var legalNoticeText: String {
        let intro: String?
        if let package = selected.package,
           account.eligibility[package.storeProduct.productIdentifier]?.status == .eligible,
           let discount = package.storeProduct.introductoryDiscount,
           discount.paymentMode != .freeTrial {
            intro = BillingTerms(package: package, eligibility: .eligible).introduction
        } else {
            intro = nil
        }
        return ProPlansLegalNotice.combined(
            storage: roomString("Stored only on this iPhone, excluded from backup. Export before changing phones or deleting Offzone."),
            intro: intro,
            legal: roomString("pro_legal")
        )
    }

    var body: some View {
        ProPage(spacing: 8, verticalPadding: 8) {
            if !dynamicTypeSize.isAccessibilitySize {
                Image("OffzonePro-nook-peek").resizable().scaledToFit().frame(height: 52)
                    .frame(maxWidth: .infinity).accessibilityHidden(true).proReveal(delay: 0, duration: 420, rise: 24)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(roomString("pro_plans_title")).proHeading().proReveal(delay: 120)
                if showsSummary {
                    Text(roomString("pro_plans_sub"))
                        .font(.suit(.subheadline))
                        .foregroundStyle(Color.roomInkSecondary)
                        .fixedSize(horizontal: false, vertical: true).proReveal(delay: 180)
                }
            }
            VStack(spacing: 8) {
                ForEach(Array(plans.enumerated()), id: \.element.id) { i, plan in
                    ProPlanCard(plan: plan, selected: selected.id == plan.id,
                                savings: plan.annual ? ProPlanMath.savingsPercent(annual: annual, monthly: monthly) : nil,
                                perMonth: plan.annual ? ProPlanMath.monthlyEquivalent(annual: annual, monthly: monthly).flatMap { ProPlanMath.money($0, currency: plan.currency) } : nil,
                                burst: plan.annual ? burst : 0) {
                        withAnimation(reduceMotion ? .linear(duration: 0.1) : ProMotion.state(duration: 0.22)) { select(plan) }
                    }
                    .disabled(busy).proReveal(delay: 200 + i * 90, rise: 24)
                    .id(plan.annual ? "pro.plan.annual" : "pro.plan.monthly")
                }
            }
            Text(legalNoticeText)
                .font(.custom(Font.suitFace(.regular), size: 13, relativeTo: .footnote))
                .foregroundStyle(Color.roomInkSecondary)
                .fixedSize(horizontal: false, vertical: true)
            PaywallSecondaryActions(
                showsSignIn: showsSignInAction,
                busy: busy,
                restore: restore,
                signIn: signIn
            )
            PaywallUtilityLinks(
                termsURL: account.termsURL,
                privacyURL: account.privacyURL,
                openAccount: openAccount
            )
            if let error = account.errorMessage { Text(error).font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary) }
            if let notice { Text(notice).font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary) }
        } footer: {
            if !signedInForPurchase {
                Text(roomString("pro_sign_in_note"))
                    .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if selected.trialDays != nil, let zero = ProPlanMath.money(0, currency: selected.currency) {
                Text(roomString("pro_due_today", zero)).font(.suit(.subheadline, weight: .semibold))
            }
            Button(action: purchase) {
                Group {
                    if busy { ProgressView().tint(Color.roomCanvas) }
                    else {
                        Text(roomString(selected.trialDays != nil ? "pro_start_trial" : "Subscribe"))
                    }
                }.frame(maxWidth: .infinity).multilineTextAlignment(.center)
            }.roomPrimaryAction().disabled(busy).proShimmer(disabled: busy).accessibilityIdentifier("pro.purchase")
        }
        .task(id: reduceMotion) {
            guard !reduceMotion && selected.annual else { return }
            do { try await Task.sleep(for: .milliseconds(700)); burst += 1 } catch { }
        }
        .onChange(of: selected.id) { _, _ in if selected.annual && !reduceMotion { burst += 1 } }
    }
}

struct ProPage<Content: View, Footer: View>: View {
    var centered = false
    var spacing: CGFloat = 16
    var verticalPadding: CGFloat = 16
    @ViewBuilder let content: () -> Content
    @ViewBuilder let footer: () -> Footer
    var body: some View {
        GeometryReader { proxy in
            ScrollViewReader { reader in
                ScrollView {
                    VStack(alignment: .leading, spacing: spacing) {
                        content()
                        Color.clear.frame(height: 1).id("pro.page.bottom")
                    }
                    .frame(maxWidth: 560).frame(maxWidth: .infinity)
                    .frame(minHeight: max(0, proxy.size.height - verticalPadding * 2), alignment: centered ? .center : .top)
                    .padding(.horizontal, 24).padding(.vertical, verticalPadding)
                }
#if DEBUG
                .task {
                    guard let target = ProPaywallPreview.scrollTarget else { return }
                    do {
                        try await Task.sleep(for: .milliseconds(800))
                        reader.scrollTo(target, anchor: .bottom)
                    } catch { }
                }
#endif
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 4, content: footer).frame(maxWidth: 560).frame(maxWidth: .infinity)
                .padding(.horizontal, 24).padding(.top, 8).padding(.bottom, 6).background(Color.roomCanvas)
        }
    }
}

private struct ProBenefits: View {
    var benefits = ["pro_benefit_plan", "pro_benefit_journal", "pro_benefit_review"]
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(benefits.enumerated()), id: \.offset) { i, benefit in
                HStack(alignment: .firstTextBaseline, spacing: 14) {
                    Image(systemName: "checkmark").foregroundStyle(Color.roomAccent).accessibilityHidden(true)
                    Text(roomString(benefit)).fixedSize(horizontal: false, vertical: true)
                }.frame(maxWidth: .infinity, minHeight: 58, alignment: .leading).padding(.vertical, 8)
                if i < benefits.count - 1 { Divider().overlay(Color.roomLine) }
            }
        }.padding(.horizontal, 20).editorialPanel()
    }
}

private struct ProShift: ViewModifier {
    let opacity: Double
    let x: CGFloat
    func body(content: Content) -> some View { content.opacity(opacity).offset(x: x) }
}

extension View {
    func proHeading() -> some View {
        font(.suit(.title, weight: .bold)).fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
    }
}

private extension FocusGoal {
    var proHeadlineKey: String {
        switch self {
        case .work: "pro_headline_work"
        case .rest: "pro_headline_rest"
        case .presence: "pro_headline_presence"
        case .personal: "pro_headline_personal"
        }
    }
}
