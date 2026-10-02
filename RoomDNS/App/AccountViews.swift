import AuthenticationServices
import RevenueCat
import GoogleSignInSwift
import SwiftUI

struct RoomAccountView: View {
    let goal: FocusGoal
    @EnvironmentObject private var account: AccountStore
    @Environment(\.dismiss) private var dismiss
    @State private var deleting = false
    @State private var paywallPresented = false
    @State private var journalPresented = false
    @State private var restored = false
    @State private var emailFlow: EmailAccountFlow?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    RoomSpirit(state: .welcome).frame(height: 132).frame(maxWidth: .infinity)
                        .background(Color.roomButter).clipShape(RoundedRectangle(cornerRadius: 24))
                    Text(account.isPro ? roomString("Offzone Pro") : roomString("Your free space"))
                        .font(.suit(.largeTitle, weight: .bold))
                    Text("Your rules and free recovery stay on this iPhone.")
                        .foregroundStyle(Color.roomInkSecondary)
                    Button { journalPresented = true } label: {
                        Label("Plan & reflect", systemImage: "book.closed")
                    }.frame(minHeight: 44)
                    Divider().overlay(Color.roomLine)
                    if account.isSignedIn {
                        Label(account.accountName, systemImage: "person.crop.circle.fill")
                            .font(.suit(.headline))
                        Text("Save your goal to your account. App selections and places stay on this iPhone.")
                        Text(roomString("Goal on this iPhone: %@", roomString(goal.title))).font(.suit(.subheadline))
                        if let saved = account.savedGoal.flatMap(FocusGoal.init(rawValue:)) {
                            Text(roomString("Saved account goal: %@", roomString(saved.title))).font(.suit(.subheadline))
                        }
                        Button { Task { await account.saveGoal(goal.rawValue) } } label: {
                            Label(account.savedGoal == goal.rawValue ? roomString("Goal saved") : roomString("Save my goal"),
                                  systemImage: account.savedGoal == goal.rawValue ? "checkmark" : "arrow.up.circle")
                        }.frame(minHeight: 44).disabled(account.isBusy || account.savedGoal == goal.rawValue)
                        if account.isPro {
                            Link("Manage subscription", destination: URL(string: "https://apps.apple.com/account/subscriptions")!)
                                .foregroundStyle(Color.roomAccent).frame(minHeight: 44)
                        } else if account.canShowOffer {
                            Button("Explore Pro") { paywallPresented = true }.roomPrimaryAction()
                        }
                        if account.purchasesConfigured {
                            Button("Restore purchases") {
                                Task { await account.restorePurchases(); restored = account.errorMessage == nil }
                            }.frame(minHeight: 44).disabled(account.isBusy)
                            if restored {
                                Text(account.isPro ? roomString("Your Pro subscription is active.") : roomString("No active Pro subscription was found."))
                                    .font(.suit(.subheadline))
                            }
                        }
                        if account.providerIDs.contains("password") && !account.emailVerified {
                            Text(account.needsEmailVerification
                                 ? roomString("Verify your email to save your goal and manage purchases.")
                                 : roomString("Verify your linked email address.")).font(.suit(.subheadline))
                            Button("Resend verification email") { Task { await account.sendEmailVerification() } }
                                .frame(minHeight: 44).disabled(account.isBusy)
                            Button("I verified my email") { Task { await account.reloadEmailVerification() } }
                                .frame(minHeight: 44).disabled(account.isBusy)
                        }
                        if !account.needsEmailVerification {
                            DisclosureGroup("Linked sign-in methods") {
                                Text("Link another sign-in method to keep this account and its purchases.")
                                    .font(.suit(.footnote)).padding(.vertical, 8)
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
                            Text("Delete your account and saved goal? Your local rules stay here. Deleting an account does not cancel an App Store subscription.")
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
                        Text("Keep your goal with you.")
                            .font(.suit(.title2, weight: .bold))
                        Text("Sign in to save your goal and manage purchases. You can keep using Offzone for free.")
                        appleButton(deleting: false)
                        if account.googleSignInConfigured {
                            GoogleSignInButton { Task { await account.signInWithGoogle() } }
                                .frame(minHeight: 56).disabled(account.isBusy)
                        }
                        Button("Continue with email") { emailFlow = .signIn }
                            .roomSecondaryAction()
                            .disabled(account.isBusy)
                    } else {
                        Text("Account sign-in isn’t available yet. You can keep using Offzone for free.")
                            .foregroundStyle(Color.roomInkSecondary)
                    }
                    if account.isBusy { ProgressView().frame(maxWidth: .infinity).accessibilityLabel("Please wait") }
                    if let message = account.statusMessage { Text(message).font(.suit(.subheadline)).accessibilityAddTraits(.updatesFrequently) }
                    if let error = account.errorMessage { Text(error).foregroundStyle(Color.roomWarning).font(.suit(.subheadline)) }
                    if let terms = account.termsURL { Link("Terms of use", destination: terms).foregroundStyle(Color.roomAccent).frame(minHeight: 44) }
                    if let privacy = account.privacyURL { Link("Privacy policy", destination: privacy).foregroundStyle(Color.roomAccent).frame(minHeight: 44) }
                }
                .font(.suit(.body)).foregroundStyle(Color.roomInk).roomTextActions()
                .padding(24).frame(maxWidth: 560).frame(maxWidth: .infinity)
            }
            .background(Color.roomCanvas).navigationTitle("Account & plan").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .tint(Color.roomAction).preferredColorScheme(.light)
        .sheet(item: $emailFlow) { EmailAccountView(mode: $0).presentationCornerRadius(24) }
        .sheet(isPresented: $paywallPresented) { RoomPaywallView(goal: goal).presentationCornerRadius(24) }
        .sheet(isPresented: $journalPresented) {
            NavigationStack {
                ProJournalView(goal: goal)
                    .toolbar { ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { journalPresented = false }
                    } }
            }
            .presentationCornerRadius(24)
        }
        .task { await account.refresh(); await account.loadOfferings() }
        .onChange(of: account.isSignedIn) { _, signedIn in
            deleting = false; restored = false
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
                        Text("Use at least 8 characters.").font(.suit(.footnote))
                    }
                }
                .listRowBackground(Color.roomCard)
                if mode == .link {
                    Text("Link another sign-in method to keep this account and its purchases.")
                        .listRowBackground(Color.roomCard)
                }
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
