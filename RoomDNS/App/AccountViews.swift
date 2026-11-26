import AuthenticationServices
import RevenueCat
import GoogleSignInSwift
import SwiftUI

enum AccountViewPolicy {
    static func showsSignInMethods(hasProAccess: Bool, signInExpanded: Bool) -> Bool {
        !hasProAccess || signInExpanded
    }

    static func showsAccountStatusMessage(_ statusMessage: String?, matching restoreStatusMessage: String?) -> Bool {
        guard let statusMessage, !statusMessage.isEmpty else { return false }
        return statusMessage != restoreStatusMessage
    }

    static func showsManageSubscriptionOnOverview(hasProAccess: Bool) -> Bool {
        hasProAccess
    }

    static func showsUpgradeOnOverview(hasProAccess: Bool, starterRemainingDays: Int) -> Bool {
        !hasProAccess && starterRemainingDays > 0
    }

    static func subscriptionStatusKey(hasProAccess: Bool) -> String {
        hasProAccess ? "Your Pro subscription is active." : "No active subscription."
    }

    static func showsJournalLink(hasProAccess: Bool, hasLocalJournalFile: Bool) -> Bool {
        hasProAccess || hasLocalJournalFile
    }
}

private enum AccountRoute: Hashable {
    case manage
}

struct RoomAccountView: View {
    let goal: FocusGoal
    var startInManage = false
    var onUpgrade: (() -> Void)? = nil
    @EnvironmentObject private var account: AccountStore
    @Environment(\.dismiss) private var dismiss
    @State private var path: [AccountRoute]
    @State private var feedbackPresented = false

    init(goal: FocusGoal, startInManage: Bool = false, onUpgrade: (() -> Void)? = nil) {
        self.goal = goal
        self.startInManage = startInManage
        self.onUpgrade = onUpgrade
        _path = State(initialValue: startInManage ? [.manage] : [])
    }

    private var overviewTitle: String {
        if account.hasProAccess { return roomString("Offzone Pro") }
        if account.isStarterAccessActive { return roomString("starter_title") }
        return roomString("Account")
    }

    private var overviewStatus: String {
        if account.hasProAccess {
            return roomString(AccountViewPolicy.subscriptionStatusKey(hasProAccess: true))
        }
        if account.isStarterAccessActive {
            return account.starterRemainingDays == 1
                ? roomString("starter_days_left_one")
                : roomString("starter_days_left_many", account.starterRemainingDays)
        }
        return roomString(AccountViewPolicy.subscriptionStatusKey(hasProAccess: false))
    }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    RoomSpirit(state: .welcome)
                        .frame(height: 132)
                        .frame(maxWidth: .infinity)
                        .background(Color.roomButter)
                        .clipShape(RoundedRectangle(cornerRadius: 24))
                    VStack(alignment: .leading, spacing: 8) {
                        Text(overviewTitle)
                            .font(.suit(.largeTitle, weight: .bold))
                        Text(overviewStatus)
                            .font(.suit(.body))
                            .foregroundStyle(Color.roomInkSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("account.subscriptionStatus")
                    }
                    if AccountViewPolicy.showsManageSubscriptionOnOverview(hasProAccess: account.hasProAccess) {
                        Link(destination: URL(string: "https://apps.apple.com/account/subscriptions")!) {
                            Text("Manage subscription")
                                .font(.suit(.body, weight: .semibold))
                                .foregroundStyle(Color.roomCanvas)
                                .padding(.horizontal, 24).padding(.vertical, 12)
                                .frame(maxWidth: .infinity, minHeight: 56)
                                .background(Color.roomAccent, in: Capsule())
                        }
                        .accessibilityIdentifier("account.manageSubscription")
                    } else if AccountViewPolicy.showsUpgradeOnOverview(
                        hasProAccess: account.hasProAccess,
                        starterRemainingDays: account.starterRemainingDays
                    ), let onUpgrade {
                        Button(roomString("pro_see_plans"), action: onUpgrade)
                            .roomPrimaryAction()
                            .accessibilityIdentifier("account.seeProPlans")
                    }
                    Button {
                        feedbackPresented = true
                    } label: {
                        Label(roomString("feedback_open"), systemImage: "bubble.left.and.text.bubble.right")
                    }
                    .roomSecondaryAction()
                    .accessibilityIdentifier("account.feedback")
                    NavigationLink(value: AccountRoute.manage) {
                        HStack(spacing: 6) {
                            Text("Manage account")
                            Image(systemName: "chevron.right")
                                .font(.suit(.caption, weight: .semibold))
                                .accessibilityHidden(true)
                        }
                        .font(.suit(.subheadline, weight: .medium))
                        .foregroundStyle(Color.roomAccent)
                        .frame(minHeight: 44)
                    }
                    .accessibilityIdentifier("account.manageAccount")
                }
                .font(.suit(.body)).foregroundStyle(Color.roomInk)
                .padding(24).frame(maxWidth: 560, alignment: .leading).frame(maxWidth: .infinity)
            }
            .background(Color.roomCanvas)
            .navigationTitle("Account & plan").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .navigationDestination(for: AccountRoute.self) { _ in
                AccountManageView(goal: goal, dismissSheet: { dismiss() })
            }
            .sheet(isPresented: $feedbackPresented) {
                FeedbackView().presentationCornerRadius(24)
            }
        }
        .tint(Color.roomAction).preferredColorScheme(.light)
        .task { await account.refresh(); await account.loadOfferings() }
    }
}

private struct AccountManageView: View {
    let goal: FocusGoal
    let dismissSheet: () -> Void
    @EnvironmentObject private var account: AccountStore
    @State private var deleting = false
    @State private var journalPresented = false
    @State private var restored = false
    @State private var signInExpanded = false
    @State private var emailFlow: EmailAccountFlow?

    private var restoreStatusMessage: String? {
        guard account.isSignedIn, account.purchasesConfigured, restored else { return nil }
        return account.hasProAccess
            ? roomString("Your Pro subscription is active.")
            : roomString("No active Pro subscription was found.")
    }

    private var showsJournalLink: Bool {
        AccountViewPolicy.showsJournalLink(
            hasProAccess: account.hasAppAccess,
            hasLocalJournalFile: FileManager.default.fileExists(atPath: ProJournalStore.defaultURL.path)
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if showsJournalLink {
                    Button { journalPresented = true } label: {
                        Label("Plan & reflect", systemImage: "book.closed")
                    }
                    .frame(minHeight: 44)
                    .accessibilityIdentifier("account.manage.journal")
                    Divider().overlay(Color.roomLine)
                }
                if account.isSignedIn {
                    Label(account.accountName, systemImage: "person.crop.circle.fill")
                        .font(.suit(.headline))
                    if account.purchasesConfigured {
                        Button("Restore purchases") {
                            Task { await account.restorePurchases(); restored = account.errorMessage == nil }
                        }.frame(minHeight: 44).disabled(account.isBusy)
                        if let restoreStatusMessage {
                            Text(restoreStatusMessage)
                                .font(.suit(.subheadline))
                        }
                    }
                    if account.providerIDs.contains("password") && !account.emailVerified {
                        Text(account.needsEmailVerification
                             ? roomString("Verify your email to manage your account and purchases.")
                             : roomString("Verify your linked email address.")).font(.suit(.subheadline))
                        Button("Resend verification email") { Task { await account.sendEmailVerification() } }
                            .frame(minHeight: 44).disabled(account.isBusy)
                        Button("I verified my email") { Task { await account.reloadEmailVerification() } }
                            .frame(minHeight: 44).disabled(account.isBusy)
                    }
                    if !account.needsEmailVerification {
                        DisclosureGroup("Linked sign-in methods") {
                            Text("Link another sign-in method to keep this account and its purchases.")
                                .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary).padding(.vertical, 8)
                            if account.providerIDs.contains("apple.com") {
                                Label("Apple linked", systemImage: "checkmark")
                            } else {
                                appleButton(deleting: false, linking: true)
                            }
                            if account.providerIDs.contains("google.com") {
                                Label("Google linked", systemImage: "checkmark")
                            } else if account.googleSignInConfigured {
                                GoogleSignInButton { Task { await account.signInWithGoogle(linking: true) } }
                                    .disabled(account.isBusy)
                            }
                            if account.providerIDs.contains("password") {
                                Label("Email linked", systemImage: "checkmark")
                                Button("Reset password") { Task { await account.sendPasswordReset(email: account.email) } }
                                    .frame(minHeight: 44).disabled(account.isBusy)
                            } else {
                                Button("Link email and password") { emailFlow = .link }
                                    .frame(minHeight: 44).disabled(account.isBusy)
                            }
                        }
                    }
                    Button("Sign out") { Task { await account.signOut() } }
                        .frame(minHeight: 44).disabled(account.isBusy)
                    if deleting {
                        Text("Delete your account? Your local rules stay here. Deleting an account does not cancel an App Store subscription.")
                            .font(.suit(.subheadline))
                        Link("Manage subscription", destination: URL(string: "https://apps.apple.com/account/subscriptions")!)
                            .foregroundStyle(Color.roomAccent).frame(minHeight: 44)
                        if account.providerIDs.contains("apple.com") {
                            Text("Confirm with Apple to delete your account.").font(.suit(.headline))
                            appleButton(deleting: true)
                        } else if account.providerIDs.contains("google.com") {
                            Text("Confirm with Google to delete your account.").font(.suit(.headline))
                            GoogleSignInButton { Task { await account.deleteAccountWithGoogle() } }
                                .disabled(account.isBusy)
                        } else if account.providerIDs.contains("password") {
                            Button("Confirm with password") { emailFlow = .delete }
                                .frame(minHeight: 44).disabled(account.isBusy)
                        }
                        Button("Keep account") { deleting = false }.frame(minHeight: 44)
                    } else {
                        Button("Delete account", role: .destructive) { deleting = true }
                            .frame(minHeight: 44).disabled(account.isBusy)
                    }
                } else if account.isConfigured {
                    if AccountViewPolicy.showsSignInMethods(hasProAccess: account.hasProAccess, signInExpanded: signInExpanded) {
                        Text("Sign in to Offzone")
                            .font(.suit(.title2, weight: .bold))
                        Text("Sign in to manage your subscription and restore purchases across devices.")
                        appleButton(deleting: false)
                        if account.googleSignInConfigured {
                            GoogleSignInButton { Task { await account.signInWithGoogle() } }
                                .frame(minHeight: 56).disabled(account.isBusy)
                        }
                        Button("Continue with email") { emailFlow = .signIn }
                            .roomSecondaryAction()
                            .disabled(account.isBusy)
                    } else {
                        Button("Sign in to Offzone") {
                            signInExpanded = true
                        }
                        .roomSecondaryAction()
                        .disabled(account.isBusy)
                        .accessibilityIdentifier("account.expandSignIn")
                    }
                } else {
                    Text("Account sign-in isn’t available right now.")
                        .foregroundStyle(Color.roomInkSecondary)
                }
                if account.isBusy { ProgressView().frame(maxWidth: .infinity).accessibilityLabel("Please wait") }
                if AccountViewPolicy.showsAccountStatusMessage(account.statusMessage, matching: restoreStatusMessage),
                   let message = account.statusMessage {
                    Text(message).font(.suit(.subheadline)).accessibilityAddTraits(.updatesFrequently)
                }
                if let error = account.errorMessage { Text(error).foregroundStyle(Color.roomWarning).font(.suit(.subheadline)) }
                Divider().overlay(Color.roomLine)
                if let terms = account.termsURL {
                    Link("Terms of use", destination: terms).foregroundStyle(Color.roomAccent).frame(minHeight: 44)
                }
                if let privacy = account.privacyURL {
                    Link("Privacy policy", destination: privacy).foregroundStyle(Color.roomAccent).frame(minHeight: 44)
                }
            }
            .font(.suit(.body)).foregroundStyle(Color.roomInk).roomTextActions()
            .padding(24).frame(maxWidth: 560, alignment: .leading).frame(maxWidth: .infinity)
        }
        .background(Color.roomCanvas)
        .navigationTitle("Manage account").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismissSheet() } } }
        .sheet(item: $emailFlow) { EmailAccountView(mode: $0).presentationCornerRadius(24) }
        .sheet(isPresented: $journalPresented) {
            NavigationStack {
                ProJournalView(goal: goal)
            }
            .presentationCornerRadius(24)
        }
        .onChange(of: account.isSignedIn) { _, signedIn in
            deleting = false; restored = false; signInExpanded = false
            if signedIn { Task { await account.loadOfferings() } }
        }
    }

    private func appleButton(deleting: Bool, linking: Bool = false) -> some View {
        SignInWithAppleButton(.continue) { request in
            account.prepareAppleRequest(request, deleting: deleting, linking: linking)
        } onCompletion: { result in
            Task { await account.completeAppleSignIn(result) }
        }
        .signInWithAppleButtonStyle(.whiteOutline).frame(height: 56)
        .disabled(account.isBusy)
    }
}

struct FeedbackView: View {
    @EnvironmentObject private var account: AccountStore
    @Environment(\.dismiss) private var dismiss
    @State private var category: FeedbackCategory = .bug
    @State private var message = ""
    @State private var contact = ""
    @State private var sending = false
    @State private var sent = false
    @State private var error: String?

    private static let issuesURL = URL(string: "https://github.com/crazydumbbell/offzone/issues")!

    var body: some View {
        NavigationStack {
            Form {
                if sent {
                    Section {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(roomString("feedback_sent_title"))
                                .font(.suit(.headline, weight: .bold))
                                .foregroundStyle(Color.roomInk)
                            Text(roomString("feedback_sent_body"))
                                .font(.suit(.body))
                                .foregroundStyle(Color.roomInkSecondary)
                        }
                        .padding(.vertical, 6)
                    }
                    .listRowBackground(Color.roomMint)
                } else {
                    Section {
                        Picker(roomString("feedback_category"), selection: $category) {
                            ForEach(FeedbackCategory.allCases) { item in
                                Text(roomString(item.titleKey)).tag(item)
                            }
                        }
                        .pickerStyle(.segmented)
                        TextField(
                            roomString("feedback_placeholder"),
                            text: $message,
                            axis: .vertical
                        )
                        .lineLimit(4...8)
                        .onChange(of: message) { _, value in
                            if value.count > FeedbackValidation.maxMessageLength {
                                message = String(value.prefix(FeedbackValidation.maxMessageLength))
                            }
                        }
                        TextField(roomString("feedback_contact_placeholder"), text: $contact)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .onChange(of: contact) { _, value in
                                if value.count > FeedbackValidation.maxContactLength {
                                    contact = String(value.prefix(FeedbackValidation.maxContactLength))
                                }
                            }
                    } footer: {
                        Text(roomString("feedback_privacy_note"))
                            .font(.suit(.subheadline))
                            .foregroundStyle(Color.roomInkSecondary)
                    }
                    .listRowBackground(Color.roomCard)

                    if let error {
                        Section {
                            Text(error)
                                .font(.suit(.subheadline))
                                .foregroundStyle(Color.roomWarning)
                        }
                        .listRowBackground(Color.roomCard)
                    }

                    Section {
                        Button {
                            error = nil
                            sending = true
                            Task {
                                let resultError = await account.submitFeedback(
                                    category: category,
                                    message: message,
                                    contact: contact
                                )
                                sending = false
                                if let resultError {
                                    error = resultError
                                } else {
                                    sent = true
                                }
                            }
                        } label: {
                            Group {
                                if sending {
                                    ProgressView().tint(Color.roomCanvas)
                                } else {
                                    Text(roomString("feedback_send"))
                                }
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .roomPrimaryAction()
                        .disabled(sending || FeedbackValidation.sanitize(message: message, contact: contact) == nil)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                    }
                }

                Section {
                    Link(roomString("feedback_open_github"), destination: Self.issuesURL)
                        .foregroundStyle(Color.roomAccent)
                        .frame(minHeight: 44)
                }
                .listRowBackground(Color.roomCard)
            }
            .font(.suit(.body))
            .foregroundStyle(Color.roomInk)
            .scrollContentBackground(.hidden)
            .background(Color.roomCanvas)
            .navigationTitle(roomString("feedback_open"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(roomString("Done")) { dismiss() }
                }
            }
        }
        .tint(Color.roomAction)
        .preferredColorScheme(.light)
    }
}

private enum EmailAccountFlow: String, Identifiable {
    case signIn, create, link, delete
    var id: String { rawValue }
}

private struct EmailAccountView: View {
    @State var mode: EmailAccountFlow
    @EnvironmentObject private var account: AccountStore
    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var password = ""
    @State private var confirmation = ""
    @State private var validation: String?

    private var newPassword: Bool { mode == .create || mode == .link }
    private var title: String {
        switch mode {
        case .signIn: return roomString("Sign in with email")
        case .create: return roomString("Create account")
        case .link: return roomString("Link email and password")
        case .delete: return roomString("Delete account")
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if mode == .delete {
                        Text(account.email)
                        Text("Enter your password to delete your account. This does not cancel your App Store subscription.")
                    } else {
                        TextField("Email", text: $email)
                            .textContentType(.username).keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                    }
                    SecureField("Password", text: $password)
                        .textContentType(newPassword ? .newPassword : .password)
                    if newPassword {
                        SecureField("Confirm password", text: $confirmation).textContentType(.newPassword)
                        Text("Use at least 8 characters.").font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                    }
                }
                .listRowBackground(Color.roomCard)
                if let message = validation ?? account.errorMessage {
                    Text(message).foregroundStyle(Color.roomWarning).listRowBackground(Color.roomCard)
                }
                if let message = account.statusMessage { Text(message).listRowBackground(Color.roomCard) }
                Section {
                    Button(title) {
                        validation = nil
                        guard !newPassword || password == confirmation else {
                            validation = roomString("Passwords do not match.")
                            return
                        }
                        Task {
                            switch mode {
                            case .signIn: await account.signInWithEmail(email: email, password: password)
                            case .create: await account.createEmailAccount(email: email, password: password)
                            case .link: await account.linkEmailAccount(email: email, password: password)
                            case .delete: await account.deleteAccountWithPassword(password)
                            }
                            password = ""; confirmation = ""
                            if account.errorMessage == nil { dismiss() }
                        }
                    }
                    .frame(minHeight: 44).disabled(account.isBusy)
                    if mode == .signIn {
                        Button("Forgot password?") {
                            validation = nil
                            Task { await account.sendPasswordReset(email: email) }
                        }.frame(minHeight: 44).disabled(account.isBusy)
                        Button("Create account") {
                            mode = .create; password = ""; confirmation = ""; validation = nil
                            account.errorMessage = nil
                        }.frame(minHeight: 44).disabled(account.isBusy)
                    }
                    if account.isBusy { ProgressView().accessibilityLabel("Please wait") }
                }
                .listRowBackground(Color.roomCard)
            }
            .font(.suit(.body))
            .scrollContentBackground(.hidden).background(Color.roomCanvas)
            .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }.disabled(account.isBusy)
            } }
            .tint(Color.roomAction)
            .onAppear { account.errorMessage = nil }
        }
    }
}

struct BillingTerms {
    let action: String
    let introduction: String
    let renewal: String
    let firstBillingDate: Date?

    init(package: Package, eligibility: IntroEligibilityStatus?) {
        let product = package.storeProduct
        renewal = Self.renewal(for: product)
        if eligibility == .eligible, let intro = product.introductoryDiscount, intro.paymentMode == .freeTrial {
            firstBillingDate = Self.endDate(intro.subscriptionPeriod, count: intro.numberOfPeriods)
        } else { firstBillingDate = nil }
        guard eligibility == .eligible, let intro = product.introductoryDiscount else {
            action = roomString("Subscribe")
            introduction = eligibility == .unknown || eligibility == nil
                ? roomString("Apple will confirm any available offer before you subscribe.")
                : roomString("Your subscription starts when you confirm with Apple.")
            return
        }
        let duration = Self.duration(intro.subscriptionPeriod, count: intro.numberOfPeriods)
        switch intro.paymentMode {
        case .freeTrial:
            action = roomString("Try free for %@", duration)
            introduction = roomString("%@ free, then %@. Apple confirms the first billing date before you subscribe.", duration, renewal)
        case .payAsYouGo:
            action = roomString("Subscribe")
            introduction = roomString("Intro offer: %@ every %@ for %d payments. Then %@.", intro.localizedPriceString,
                                      Self.duration(intro.subscriptionPeriod), intro.numberOfPeriods, renewal)
        case .payUpFront:
            action = roomString("Subscribe")
            introduction = roomString("Intro offer: %@ for %@. Then %@.", intro.localizedPriceString, duration, renewal)
        @unknown default:
            action = roomString("Subscribe")
            introduction = roomString("Apple will confirm any available offer before you subscribe.")
        }
    }

    static func renewal(for product: StoreProduct) -> String {
        guard let period = product.subscriptionPeriod else { return product.localizedPriceString }
        return roomString("%@ every %@", product.localizedPriceString, duration(period))
    }

    static func duration(_ period: SubscriptionPeriod, count: Int = 1) -> String {
        let formatter = DateComponentsFormatter()
        formatter.unitsStyle = .full
        var components = DateComponents()
        let value = period.value * count
        switch period.unit {
        case .day: components.day = value; formatter.allowedUnits = .day
        case .week: components.weekOfMonth = value; formatter.allowedUnits = .weekOfMonth
        case .month: components.month = value; formatter.allowedUnits = .month
        case .year: components.year = value; formatter.allowedUnits = .year
        @unknown default: return ""
        }
        return formatter.string(from: components) ?? ""
    }

    static func endDate(_ period: SubscriptionPeriod, count: Int = 1, starting: Date = .now,
                        calendar: Calendar = .current) -> Date? {
        let component: Calendar.Component
        switch period.unit {
        case .day: component = .day
        case .week: component = .weekOfYear
        case .month: component = .month
        case .year: component = .year
        @unknown default: return nil
        }
        return calendar.date(byAdding: component, value: period.value * count, to: starting)
    }
}

extension View {
    func roomPrimaryAction() -> some View {
        buttonStyle(RoomPrimaryActionStyle())
    }

    func roomSecondaryAction() -> some View {
        buttonStyle(RoomSecondaryActionStyle())
    }

    func roomTextActions() -> some View {
        buttonStyle(RoomTextButtonStyle())
    }
}

// Text action: Pine label, system red for destructive. Used where an Ink container would otherwise recolor plain buttons.
private struct RoomTextButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: SwiftUI.ButtonStyleConfiguration) -> some View {
        configuration.label
            .foregroundStyle(configuration.role == .destructive ? Color.red : Color.roomAccent)
            .opacity(!isEnabled ? 0.4 : configuration.isPressed ? 0.6 : 1)
            .contentShape(Rectangle())
    }
}

// Pine pill with Butter text. Disabled: Ink 10% fill, Ink 38% text.
private struct RoomPrimaryActionStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: SwiftUI.ButtonStyleConfiguration) -> some View {
        configuration.label.font(.suit(.body, weight: .semibold))
            .foregroundStyle(isEnabled ? Color.roomCanvas : Color.roomDisabledText)
            .padding(.horizontal, 24).padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(isEnabled ? Color.roomAccent : Color.roomDisabledFill, in: Capsule())
            .contentShape(Capsule()).opacity(configuration.isPressed ? 0.8 : 1)
    }
}

// Ivory pill, Pine 28% outline, Ink text. Same disabled colors as the primary.
struct RoomSecondaryActionStyle: ButtonStyle {
    var horizontalPadding: CGFloat = 24
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: SwiftUI.ButtonStyleConfiguration) -> some View {
        configuration.label.font(.suit(.body, weight: .medium))
            .foregroundStyle(isEnabled ? Color.roomInk : Color.roomDisabledText)
            .padding(.horizontal, horizontalPadding).padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(isEnabled ? Color.roomCard : Color.roomDisabledFill, in: Capsule())
            .overlay { if isEnabled { Capsule().strokeBorder(Color.roomOutline, lineWidth: 1) } }
            .contentShape(Capsule()).opacity(configuration.isPressed ? 0.8 : 1)
    }
}
