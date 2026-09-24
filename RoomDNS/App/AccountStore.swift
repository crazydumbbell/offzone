import AuthenticationServices
import Combine
import CryptoKit
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import FirebaseFunctions
import Foundation
import GoogleSignIn
import UIKit
import RevenueCat
import Security

/// Account and store work never controls the local restriction/recovery engine.
@MainActor
final class AccountStore: ObservableObject {
    @Published private(set) var isConfigured = false
    @Published private(set) var isSignedIn = false
    @Published private(set) var accountName = ""
    @Published private(set) var isBusy = false
    @Published var errorMessage: String?
    @Published private(set) var isPro = false
    @Published private(set) var packages: [Package] = []
    @Published private(set) var eligibility: [String: IntroEligibility] = [:]
    @Published private(set) var purchasesConfigured = false
    @Published private(set) var savedGoal: String?
    @Published private(set) var unlockPassBalance: Int?
    @Published private(set) var unlockProduct: StoreProduct?
    @Published private(set) var isUnlockBusy = false
    @Published private(set) var unlockNotice: String?

    @Published private(set) var providerIDs: Set<String> = []
    @Published private(set) var email = ""
    @Published private(set) var emailVerified = false
    @Published var statusMessage: String?
    var needsEmailVerification: Bool {
        isSignedIn && !Self.identityIsVerified(providerIDs: providerIDs, emailVerified: emailVerified)
    }
    var googleSignInConfigured: Bool {
        isConfigured && !(FirebaseApp.app()?.options.clientID ?? "").isEmpty
    }

    static func identityIsVerified(providerIDs: Set<String>, emailVerified: Bool) -> Bool {
        providerIDs.contains("apple.com") || providerIDs.contains("google.com") ||
            (providerIDs.contains("password") && emailVerified)
    }

    let proBenefits: [String]
    let termsURL: URL?
    let privacyURL: URL?
    let offersEnabled: Bool
    let unlockPassEnabled: Bool
    /// Recheck at the action boundary, including while an expiry timer is suspended.
    var hasProAccess: Bool {
        Self.proAccessIsValid(signedIn: isSignedIn, currentUID: currentPurchaseUID,
                              entitlementUID: proUID, verifiedActive: isPro,
                              expirationDate: proExpirationDate, now: Date())
    }
    var canShowOffer: Bool { offersEnabled && purchasesConfigured && isSignedIn && !packages.isEmpty }
    var canPurchaseUnlockPass: Bool { unlockPassEnabled && purchasesConfigured && isSignedIn && unlockProduct != nil }
    var canUseUnlockPass: Bool { unlockPassEnabled && purchasesConfigured && isSignedIn && (unlockPassBalance ?? 0) > 0 }
    var pendingUnlockSessionID: String? {
        guard let uid = currentPurchaseUID else { return nil }
        return try? UnlockPassPendingStore.load(uid: uid)
    }

    private let sdkKey: String
    private let entitlementID: String
    private let offeringID: String
    private let unlockProductID: String
    private var authListener: AuthStateDidChangeListenerHandle?
    private var identityTask: Task<Void, Never>?
    private var nonce: String?
    private var deletingAccount = false
    private var linkingApple = false
    private var appleRequestUID: String?
    private var purchaseUID: String?
    private var proUID: String?
    private var proExpirationDate: Date?
    private var customerInfoTask: Task<Void, Never>?
    private var proExpiryTask: Task<Void, Never>?
    private var purchaseGeneration = UUID()

    deinit {
        customerInfoTask?.cancel()
        proExpiryTask?.cancel()
    }

    static func proAccessIsValid(signedIn: Bool, currentUID: String?, entitlementUID: String?,
                                 verifiedActive: Bool, expirationDate: Date?, now: Date) -> Bool {
        guard signedIn, let currentUID, currentUID == entitlementUID, verifiedActive else { return false }
        return expirationDate.map { $0 > now } ?? true
    }

    init(bundle: Bundle = .main) {
        func config(_ key: String) -> String {
            (bundle.object(forInfoDictionaryKey: key) as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        }
        func webURL(_ key: String) -> URL? {
            guard let url = URL(string: config(key)), url.scheme == "https", url.host != nil else { return nil }
            return url
        }
        sdkKey = config("RevenueCatPublicSDKKey")
        entitlementID = config("RoomProEntitlementID")
        offeringID = config("RoomProOfferingID")
        unlockProductID = config("RoomUnlockProductID")
        proBenefits = (bundle.object(forInfoDictionaryKey: "RoomProBenefits") as? [String] ?? []).filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        termsURL = webURL("RoomTermsURL")
        privacyURL = webURL("RoomPrivacyURL")
        offersEnabled = Self.offerIsReady(
            ready: bundle.object(forInfoDictionaryKey: "RoomProOfferReady") as? Bool == true,
            offeringID: offeringID, entitlementID: entitlementID, benefits: proBenefits,
            termsURL: termsURL, privacyURL: privacyURL)
        unlockPassEnabled = Self.unlockIsReady(
            ready: bundle.object(forInfoDictionaryKey: "RoomUnlockPassReady") as? Bool == true,
            productID: unlockProductID)
        // Missing configuration is an intentional offline/free state, never a fake login.
        guard let path = bundle.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path),
              options.bundleID == bundle.bundleIdentifier,
              !(options.apiKey ?? "").isEmpty, !(options.projectID ?? "").isEmpty else { return }
        if FirebaseApp.app() == nil { FirebaseApp.configure(options: options) }
        isConfigured = true
        let settings = Firestore.firestore().settings
        settings.cacheSettings = MemoryCacheSettings()
        Firestore.firestore().settings = settings
        authListener = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor [weak self] in self?.identityChanged(user) }
        }
    }

    static func offerIsReady(ready: Bool, offeringID: String, entitlementID: String,
                             benefits: [String], termsURL: URL?, privacyURL: URL?) -> Bool {
        ready && !offeringID.isEmpty && !entitlementID.isEmpty && !benefits.isEmpty &&
        termsURL?.scheme == "https" && privacyURL?.scheme == "https"
    }

    static func unlockIsReady(ready: Bool, productID: String) -> Bool {
        ready && !productID.isEmpty
    }

    func prepareAppleRequest(_ request: ASAuthorizationAppleIDRequest, deleting: Bool = false, linking: Bool = false) {
        guard isConfigured, !isBusy else { return }
        errorMessage = nil
        statusMessage = nil
        do {
            var bytes = [UInt8](repeating: 0, count: 32)
            guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else {
                throw AccountError.unavailable
            }
            let rawNonce = bytes.map { String(format: "%02x", $0) }.joined()
            nonce = rawNonce
            deletingAccount = deleting
            linkingApple = linking
            appleRequestUID = Auth.auth().currentUser?.uid
            request.requestedScopes = []
            request.nonce = SHA256.hash(data: Data(rawNonce.utf8)).map { String(format: "%02x", $0) }.joined()
        } catch { errorMessage = error.localizedDescription }
    }

    func completeAppleSignIn(_ result: Result<ASAuthorization, Error>) async {
        guard isConfigured, !isBusy else { return }
        isBusy = true
        defer { nonce = nil; deletingAccount = false; linkingApple = false; appleRequestUID = nil }
        do {
            let authorization = try result.get()
            guard let apple = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let nonce, let token = apple.identityToken,
                  let tokenString = String(data: token, encoding: .utf8) else { throw AccountError.invalidIdentity }
            let credential = OAuthProvider.appleCredential(withIDToken: tokenString, rawNonce: nonce, fullName: nil)
            if deletingAccount {
                guard let user = Auth.auth().currentUser,
                      let code = apple.authorizationCode,
                      let codeString = String(data: code, encoding: .utf8) else { throw AccountError.invalidIdentity }
                guard user.uid == appleRequestUID else { throw AccountError.invalidIdentity }
                _ = try await user.reauthenticate(with: credential)
                try await Auth.auth().revokeToken(withAuthorizationCode: codeString)
                try await deleteAuthenticatedAccount(user)
            } else {
                try await authenticate(credential, linking: linkingApple, expectedUID: appleRequestUID)
            }
            // Process the actual Firebase identity immediately; the listener is idempotent.
            identityChanged(Auth.auth().currentUser)
            await identityTask?.value
        } catch {
            if (error as? ASAuthorizationError)?.code != .canceled { reportAuthError(error) }
        }
        isBusy = false
        // The view's sign-in observer can fire while authentication is still busy.
        if isSignedIn { await loadOfferings() }
    }

    func signInWithGoogle(linking: Bool = false) async {
        let expectedUID = isConfigured ? Auth.auth().currentUser?.uid : nil
        await performAuthAction {
            let credential = try await self.googleCredential()
            try await self.authenticate(credential, linking: linking, expectedUID: expectedUID)
        }
    }

    func signInWithEmail(email: String, password: String) async {
        await performAuthAction {
            guard Auth.auth().currentUser == nil else { throw AccountError.invalidIdentity }
            let email = try Self.validatedEmail(email)
            guard !password.isEmpty else { throw AccountError.invalidPassword }
            _ = try await Auth.auth().signIn(withEmail: email, password: password)
        }
    }

    func createEmailAccount(email: String, password: String) async {
        await performAuthAction {
            guard Auth.auth().currentUser == nil else { throw AccountError.invalidIdentity }
            let email = try Self.validatedEmail(email)
            guard (8...4096).contains(password.count) else { throw AccountError.invalidPassword }
            let result = try await Auth.auth().createUser(withEmail: email, password: password)
            try await result.user.sendEmailVerification()
            self.statusMessage = NSLocalizedString("Verification email sent. Open the link, then return here to check verification.", comment: "")
        }
    }

    func linkEmailAccount(email: String, password: String) async {
        let expectedUID = isConfigured ? Auth.auth().currentUser?.uid : nil
        await performAuthAction {
            let email = try Self.validatedEmail(email)
            guard (8...4096).contains(password.count) else { throw AccountError.invalidPassword }
            let credential = EmailAuthProvider.credential(withEmail: email, password: password)
            try await self.authenticate(credential, linking: true, expectedUID: expectedUID)
            if let user = Auth.auth().currentUser, !user.isEmailVerified {
                try await user.sendEmailVerification()
                self.statusMessage = NSLocalizedString("Verification email sent. Open the link, then return here to check verification.", comment: "")
            }
        }
    }

    func sendEmailVerification() async {
        await performAuthAction {
            guard let user = Auth.auth().currentUser else { throw AccountError.invalidIdentity }
            try await user.sendEmailVerification()
            self.statusMessage = NSLocalizedString("Verification email sent. Open the link, then return here to check verification.", comment: "")
        }
    }

    func reloadEmailVerification() async {
        await performAuthAction {
            guard let user = Auth.auth().currentUser else { throw AccountError.invalidIdentity }
            try await user.reload()
            _ = try await user.getIDToken(forcingRefresh: true)
            self.statusMessage = user.isEmailVerified
                ? NSLocalizedString("Email verified.", comment: "")
                : NSLocalizedString("Email is not verified yet. Open the link in your verification email first.", comment: "")
        }
    }

    func sendPasswordReset(email: String) async {
        await performAuthAction {
            let email = try Self.validatedEmail(email)
            do { try await Auth.auth().sendPasswordReset(withEmail: email) }
            catch where (error as NSError).code == AuthErrorCode.userNotFound.rawValue { }
            self.statusMessage = NSLocalizedString("If an email account exists for this address, a password reset link has been sent.", comment: "")
        }
    }

    func deleteAccountWithGoogle() async {
        await performAuthAction {
            guard let user = Auth.auth().currentUser else { throw AccountError.invalidIdentity }
            guard !user.providerData.contains(where: { $0.providerID == "apple.com" }) else {
                throw AccountError.appleDeletionRequired
            }
            let credential = try await self.googleCredential()
            guard Auth.auth().currentUser?.uid == user.uid else { throw AccountError.invalidIdentity }
            _ = try await user.reauthenticate(with: credential)
            try await self.deleteAuthenticatedAccount(user)
        }
    }

    func deleteAccountWithPassword(_ password: String) async {
        await performAuthAction {
            guard let user = Auth.auth().currentUser, let email = user.email else { throw AccountError.invalidIdentity }
            guard !user.providerData.contains(where: { $0.providerID == "apple.com" }) else {
                throw AccountError.appleDeletionRequired
            }
            let credential = EmailAuthProvider.credential(withEmail: email, password: password)
            _ = try await user.reauthenticate(with: credential)
            try await self.deleteAuthenticatedAccount(user)
        }
    }

    static func validatedEmail(_ value: String) throws -> String {
        let email = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts = email.split(separator: "@", omittingEmptySubsequences: false)
        guard parts.count == 2, !parts[0].isEmpty, parts[1].contains("."),
              !parts[1].hasPrefix("."), !parts[1].hasSuffix("."),
              !email.contains(where: { $0.isWhitespace }), email.count <= 254 else {
            throw AccountError.invalidEmail
        }
        return email
    }

    private func verifiedIdentity(_ user: FirebaseAuth.User) -> Bool {
        Self.identityIsVerified(providerIDs: Set(user.providerData.map(\.providerID)), emailVerified: user.isEmailVerified)
    }

    private func authenticate(_ credential: AuthCredential, linking: Bool, expectedUID: String?) async throws {
        if linking {
            guard let user = Auth.auth().currentUser, user.uid == expectedUID else { throw AccountError.invalidIdentity }
            _ = try await user.link(with: credential)
            guard Auth.auth().currentUser?.uid == expectedUID else { throw AccountError.invalidIdentity }
            statusMessage = NSLocalizedString("Sign-in method linked to this account.", comment: "")
        } else {
            guard Auth.auth().currentUser == nil else { throw AccountError.invalidIdentity }
            _ = try await Auth.auth().signIn(with: credential)
        }
    }

    private func googleCredential() async throws -> AuthCredential {
        guard let clientID = FirebaseApp.app()?.options.clientID, !clientID.isEmpty,
              let scene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene,
              var presenter = scene.windows.first(where: \.isKeyWindow)?.rootViewController else {
            throw AccountError.unavailable
        }
        while let presented = presenter.presentedViewController { presenter = presented }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        guard let token = result.user.idToken?.tokenString else { throw AccountError.invalidIdentity }
        return GoogleAuthProvider.credential(withIDToken: token, accessToken: result.user.accessToken.tokenString)
    }

    private func deleteAuthenticatedAccount(_ user: FirebaseAuth.User) async throws {
        guard Auth.auth().currentUser?.uid == user.uid else { throw AccountError.invalidIdentity }
        try await Firestore.firestore().collection("users").document(user.uid).delete()
        if Auth.auth().currentUser?.uid == user.uid { savedGoal = nil }
        try await user.delete()
        try? UnlockPassPendingStore.remove(uid: user.uid)
        GIDSignIn.sharedInstance.signOut()
    }

    private func performAuthAction(_ action: () async throws -> Void) async {
        guard isConfigured, !isBusy else { return }
        isBusy = true
        errorMessage = nil
        statusMessage = nil
        Auth.auth().useAppLanguage()
        do { try await action() }
        catch { reportAuthError(error) }
        // Signup can succeed even when sending verification fails. Expose that real
        // account so the user can resend, sign out or delete it without signing up again.
        identityChanged(Auth.auth().currentUser, retry: true)
        await identityTask?.value
        isBusy = false
        if isSignedIn && !needsEmailVerification { await loadOfferings() }
    }

    private func reportAuthError(_ error: Error) {
        let nsError = error as NSError
        if nsError.domain == kGIDSignInErrorDomain && nsError.code == GIDSignInError.canceled.rawValue { return }
        if nsError.domain == AuthErrorDomain && [AuthErrorCode.accountExistsWithDifferentCredential.rawValue,
            AuthErrorCode.credentialAlreadyInUse.rawValue, AuthErrorCode.emailAlreadyInUse.rawValue].contains(nsError.code) {
            errorMessage = NSLocalizedString("This sign-in method may belong to an existing account. Sign in using its original method, then link another method in Account & plan. Accounts and purchases are not merged automatically.", comment: "")
        } else { errorMessage = error.localizedDescription }
    }

    func signOut() async {
        guard isConfigured, !isBusy else { return }
        isBusy = true
        defer { isBusy = false }
        do {
            try Auth.auth().signOut()
            GIDSignIn.sharedInstance.signOut()
            statusMessage = nil
            identityChanged(nil)
            await identityTask?.value
        } catch { errorMessage = error.localizedDescription }
    }

    func saveGoal(_ goal: String) async {
        guard ["work", "rest", "presence", "personal"].contains(goal), isConfigured,
              let user = Auth.auth().currentUser else { return }
        guard verifiedIdentity(user) else { errorMessage = AccountError.verifyEmail.localizedDescription; return }
        let uid = user.uid
        do {
            try await Firestore.firestore().collection("users").document(uid).setData([
                "goal": goal, "updatedAt": FieldValue.serverTimestamp()
            ])
            if Auth.auth().currentUser?.uid == uid { savedGoal = goal }
        } catch {
            if Auth.auth().currentUser?.uid == uid { errorMessage = error.localizedDescription }
        }
    }

    func loadOfferings() async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }
        await preparePurchaseIdentity()
        guard offersEnabled, let uid = currentPurchaseUID else { return }
        errorMessage = nil
        packages = []
        eligibility = [:]
        do {
            let offerings = try await Purchases.shared.offerings()
            guard let offering = offerings.offering(identifier: offeringID) else { throw AccountError.unavailable }
            let plans = [offering.annual, offering.monthly].compactMap { $0 }.filter { package in
                guard let period = package.storeProduct.subscriptionPeriod, period.value == 1 else { return false }
                return (package.packageType == .annual && period.unit == .year) ||
                    (package.packageType == .monthly && period.unit == .month)
            }
            guard !plans.isEmpty else { throw AccountError.unavailable }
            let eligible = await Purchases.shared.checkTrialOrIntroDiscountEligibility(plans.map { $0.storeProduct.productIdentifier })
            guard currentPurchaseUID == uid else { return }
            packages = plans
            eligibility = eligible
        } catch { errorMessage = error.localizedDescription }
    }

    func purchase(_ package: Package) async {
        guard !isBusy, !hasProAccess, canShowOffer, packages.contains(where: { $0.identifier == package.identifier && $0.storeProduct.productIdentifier == package.storeProduct.productIdentifier }),
              let uid = currentPurchaseUID else { return }
        isBusy = true
        errorMessage = nil
        defer { isBusy = false }
        do {
            let result = try await Purchases.shared.purchase(package: package)
            guard currentPurchaseUID == uid, !result.userCancelled else { return }
            apply(result.customerInfo)
        } catch { errorMessage = error.localizedDescription }
    }

    func loadUnlockPass() async {
        guard unlockPassEnabled, !isUnlockBusy else { return }
        isUnlockBusy = true
        unlockNotice = nil
        defer { isUnlockBusy = false }
        await preparePurchaseIdentity()
        guard let uid = currentPurchaseUID else { return }
        do {
            let product = await Purchases.shared.products([unlockProductID]).first
            guard currentPurchaseUID == uid, let product, product.productType == .consumable else {
                throw AccountError.unavailable
            }
            unlockProduct = product
            try await refreshUnlockBalance(for: uid)
        } catch {
            if currentPurchaseUID == uid { errorMessage = error.localizedDescription }
        }
    }

    func purchaseUnlockPass() async {
        guard !isUnlockBusy, canPurchaseUnlockPass, let product = unlockProduct,
              let uid = currentPurchaseUID else { return }
        isUnlockBusy = true
        errorMessage = nil
        unlockNotice = nil
        defer { isUnlockBusy = false }
        do {
            let result = try await Purchases.shared.purchase(product: product)
            guard currentPurchaseUID == uid else { return }
            if result.userCancelled {
                unlockNotice = NSLocalizedString("Purchase cancelled. You weren’t charged.", comment: "")
                return
            }
            apply(result.customerInfo)
            try await refreshUnlockBalance(for: uid)
            unlockNotice = NSLocalizedString("Purchase complete. Use a pass when you are ready.", comment: "")
        } catch let error as ErrorCode where error == .purchaseCancelledError {
            if currentPurchaseUID == uid {
                unlockNotice = NSLocalizedString("Purchase cancelled. You weren’t charged.", comment: "")
            }
        } catch let error as ErrorCode where error == .paymentPendingError {
            if currentPurchaseUID == uid {
                unlockNotice = NSLocalizedString("Purchase pending. Your session is still active.", comment: "")
            }
        } catch {
            if currentPurchaseUID == uid { errorMessage = error.localizedDescription }
        }
    }

    func spendUnlockPass(sessionID: String) async -> String? {
        guard !isUnlockBusy, unlockPassEnabled, purchasesConfigured, isSignedIn,
              sessionID.count == 64,
              sessionID.utf8.allSatisfy({ (48...57).contains($0) || (97...102).contains($0) }),
              let uid = currentPurchaseUID else { return nil }
        let pending: String?
        do { pending = try UnlockPassPendingStore.load(uid: uid) }
        catch {
            errorMessage = NSLocalizedString("A previous pass use still needs confirmation. This session is still active.", comment: "")
            return nil
        }
        if let pending, pending != sessionID {
            errorMessage = NSLocalizedString("A previous pass use still needs confirmation. This session is still active.", comment: "")
            return nil
        }
        guard pending == sessionID || (unlockPassBalance ?? 0) > 0 else { return nil }
        if pending == nil {
            do { try UnlockPassPendingStore.save(sessionID: sessionID, uid: uid) }
            catch {
                errorMessage = error.localizedDescription
                return nil
            }
        }
        isUnlockBusy = true
        errorMessage = nil
        unlockNotice = NSLocalizedString("Using 1 unlock pass…", comment: "")
        defer { isUnlockBusy = false }
        do {
            let callable: Callable<UnlockSpendRequest, UnlockSpendResponse> =
                Functions.functions(region: "us-central1").httpsCallable("spendUnlockPass")
            let response = try await callable(UnlockSpendRequest(sessionID: sessionID))
            guard currentPurchaseUID == uid, response.spent, response.sessionID == sessionID else {
                throw AccountError.invalidResponse
            }
            unlockPassBalance = max(0, (unlockPassBalance ?? 1) - 1)
            try? await refreshUnlockBalance(for: uid)
            unlockNotice = nil
            return sessionID
        } catch {
            if currentPurchaseUID == uid {
                unlockNotice = NSLocalizedString("We couldn’t confirm the pass. Your session is still active.", comment: "")
                errorMessage = error.localizedDescription
            }
            return nil
        }
    }

    func reconcilePreviousUnlockPass() async {
        guard !isUnlockBusy, unlockPassEnabled, purchasesConfigured, isSignedIn,
              let uid = currentPurchaseUID else { return }
        isUnlockBusy = true
        errorMessage = nil
        unlockNotice = nil
        defer { isUnlockBusy = false }
        do {
            guard let sessionID = try UnlockPassPendingStore.load(uid: uid) else { return }
            let callable: Callable<UnlockSpendRequest, UnlockReconcileResponse> =
                Functions.functions(region: "us-central1").httpsCallable("reconcileUnlockPass")
            let response = try await callable(UnlockSpendRequest(sessionID: sessionID))
            guard currentPurchaseUID == uid, response.sessionID == sessionID,
                  try UnlockPassPendingStore.load(uid: uid) == sessionID else {
                throw AccountError.invalidResponse
            }
            switch response.status {
            case "cancelled":
                try UnlockPassPendingStore.remove(uid: uid)
                unlockNotice = NSLocalizedString("The previous pass use was cancelled without a charge. You can use a pass for this session.", comment: "")
            case "spent":
                unlockNotice = NSLocalizedString("The previous pass was charged, but its session has ended. Contact support to review it. The charge record has been preserved.", comment: "")
            case "pending":
                unlockNotice = NSLocalizedString("The previous pass result is still uncertain. Contact support to review it. No new charge was requested.", comment: "")
            default:
                throw AccountError.invalidResponse
            }
            try? await refreshUnlockBalance(for: uid)
        } catch {
            if currentPurchaseUID == uid { errorMessage = error.localizedDescription }
        }
    }

    func completeUnlockPass(sessionID: String) {
        guard let uid = currentPurchaseUID else { return }
        guard (try? UnlockPassPendingStore.load(uid: uid)) == sessionID else { return }
        try? UnlockPassPendingStore.remove(uid: uid)
    }

    func restorePurchases() async {
        guard !isBusy else { return }
        isBusy = true
        errorMessage = nil
        defer { isBusy = false }
        await preparePurchaseIdentity()
        guard let uid = currentPurchaseUID else { errorMessage = AccountError.unavailable.localizedDescription; return }
        do {
            let info = try await Purchases.shared.restorePurchases()
            if currentPurchaseUID == uid {
                apply(info)
                if unlockPassEnabled { try await refreshUnlockBalance(for: uid) }
            }
        } catch { errorMessage = error.localizedDescription }
    }

    func refresh() async {
        guard !isBusy, isConfigured else { return }
        await preparePurchaseIdentity()
        guard let uid = currentPurchaseUID else { return }
        do {
            let info = try await Purchases.shared.customerInfo()
            if currentPurchaseUID == uid {
                apply(info)
                if unlockPassEnabled { try await refreshUnlockBalance(for: uid) }
            }
        } catch { errorMessage = error.localizedDescription }
    }

    private func refreshUnlockBalance(for uid: String) async throws {
        Purchases.shared.invalidateVirtualCurrenciesCache()
        let currencies = try await Purchases.shared.virtualCurrencies()
        guard currentPurchaseUID == uid else { return }
        unlockPassBalance = currencies["PASS"]?.balance ?? 0
    }

    private func preparePurchaseIdentity() async {
        await identityTask?.value
        if currentPurchaseUID == nil, isConfigured, sdkKey.hasPrefix("appl_"),
           let user = Auth.auth().currentUser {
            identityChanged(user, retry: true)
            await identityTask?.value
        }
    }

    private var currentPurchaseUID: String? {
        guard purchasesConfigured, isConfigured, let user = Auth.auth().currentUser,
              verifiedIdentity(user) else { return nil }
        let uid = user.uid
        guard purchaseUID == uid, Purchases.shared.appUserID == uid else { return nil }
        return uid
    }

    private var displayedUID: String?
    private func identityChanged(_ user: FirebaseAuth.User?, retry: Bool = false) {
        guard Auth.auth().currentUser?.uid == user?.uid else { return }
        providerIDs = Set(user?.providerData.map(\.providerID) ?? [])
        email = user?.email ?? ""
        emailVerified = user?.isEmailVerified ?? false
        guard retry || displayedUID != user?.uid || identityTask == nil else { return }
        displayedUID = user?.uid
        isSignedIn = user != nil
        accountName = user == nil ? "" : (user?.displayName ?? user?.email ?? NSLocalizedString("Account", comment: ""))
        savedGoal = nil
        customerInfoTask?.cancel()
        customerInfoTask = nil
        proExpiryTask?.cancel()
        proExpiryTask = nil
        purchaseGeneration = UUID()
        proUID = nil
        proExpirationDate = nil
        isPro = false
        packages = []
        eligibility = [:]
        unlockPassBalance = nil
        unlockProduct = nil
        unlockNotice = nil
        purchaseUID = nil
        let previous = identityTask
        let uid = user?.uid
        identityTask = Task { [weak self] in
            await previous?.value
            guard let self else { return }
            do {
                if let uid, let user, self.verifiedIdentity(user) {
                    if !self.purchasesConfigured, self.sdkKey.hasPrefix("appl_") {
                        Purchases.configure(withAPIKey: self.sdkKey, appUserID: uid)
                        self.purchasesConfigured = true
                    }
                    if self.purchasesConfigured {
                        do {
                            let info = try await Purchases.shared.logIn(uid).customerInfo
                            if Auth.auth().currentUser?.uid == uid {
                                self.purchaseUID = uid
                                self.apply(info)
                                self.observeCustomerInfo(for: uid)
                            }
                        } catch {
                            if Auth.auth().currentUser?.uid == uid { self.errorMessage = error.localizedDescription }
                        }
                    }
                    let profile = try await Firestore.firestore().collection("users").document(uid).getDocument()
                    if Auth.auth().currentUser?.uid == uid { self.savedGoal = profile.data()?["goal"] as? String }
                } else if uid == nil, self.purchasesConfigured, !Purchases.shared.isAnonymous {
                    _ = try await Purchases.shared.logOut()
                }
            } catch {
                if Auth.auth().currentUser?.uid == uid { self.errorMessage = error.localizedDescription }
            }
        }
    }

    private func observeCustomerInfo(for uid: String) {
        customerInfoTask?.cancel()
        let generation = purchaseGeneration
        let stream = Purchases.shared.customerInfoStream
        customerInfoTask = Task { [weak self] in
            for await _ in stream {
                guard !Task.isCancelled, self?.purchaseGeneration == generation,
                      self?.currentPurchaseUID == uid else { return }
                // Stream values have no account ID and may contain the previous identity's
                // buffered result. Read the SDK's current account instead of applying the event.
                guard let info = try? await Purchases.shared.customerInfo(),
                      !Task.isCancelled, self?.purchaseGeneration == generation,
                      self?.currentPurchaseUID == uid else { continue }
                self?.apply(info)
            }
        }
    }

    private func apply(_ info: CustomerInfo) {
        guard let uid = currentPurchaseUID else { return }
        proExpiryTask?.cancel()
        let entitlement = info.entitlements.activeInCurrentEnvironment[entitlementID]
        proUID = uid
        proExpirationDate = entitlement?.expirationDate
        isPro = Self.proAccessIsValid(
            signedIn: isSignedIn, currentUID: uid, entitlementUID: proUID,
            verifiedActive: info.entitlements.verification.isVerified && entitlement != nil,
            expirationDate: proExpirationDate, now: Date())
        guard isPro, let expiry = proExpirationDate else { return }
        let generation = purchaseGeneration
        proExpiryTask = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(max(0, expiry.timeIntervalSinceNow))) }
            catch { return }
            guard let self, self.purchaseGeneration == generation, self.currentPurchaseUID == uid else { return }
            self.isPro = self.hasProAccess
            await self.refresh()
        }
    }

    private enum AccountError: LocalizedError {
        case unavailable, invalidIdentity, invalidResponse, verifyEmail, invalidEmail, invalidPassword, appleDeletionRequired
        var errorDescription: String? {
            switch self {
            case .unavailable: NSLocalizedString("This service is unavailable right now. You can continue with free.", comment: "")
            case .invalidIdentity: NSLocalizedString("We could not verify this account. Please try again.", comment: "")
            case .verifyEmail: NSLocalizedString("Verify your email before saving account changes or making purchases.", comment: "")
            case .invalidEmail: NSLocalizedString("Enter a valid email address.", comment: "")
            case .invalidPassword: NSLocalizedString("Use a password with at least 8 characters.", comment: "")
            case .appleDeletionRequired: NSLocalizedString("Confirm with Apple to delete this linked account and revoke Apple access.", comment: "")
            case .invalidResponse: NSLocalizedString("We couldn’t confirm the pass. Your session is still active.", comment: "")
            }
        }
    }
}

private struct UnlockSpendRequest: Encodable {
    let sessionID: String
}

private struct UnlockSpendResponse: Decodable {
    let sessionID: String
    let spent: Bool
}

private struct UnlockReconcileResponse: Decodable {
    let sessionID: String
    let status: String
}

enum UnlockPassPendingStore {
    static func load(uid: String, directory: URL? = nil) throws -> String? {
        let url = try fileURL(uid: uid, directory: directory)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let value = String(decoding: try Data(contentsOf: url), as: UTF8.self)
        guard value.count == 64,
              value.utf8.allSatisfy({ (48...57).contains($0) || (97...102).contains($0) })
        else { throw CocoaError(.fileReadCorruptFile) }
        return value
    }

    static func save(sessionID: String, uid: String, directory: URL? = nil) throws {
        let url = try fileURL(uid: uid, directory: directory)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try Data(sessionID.utf8).write(
            to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    static func remove(uid: String, directory: URL? = nil) throws {
        let url = try fileURL(uid: uid, directory: directory)
        if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
    }

    private static func fileURL(uid: String, directory: URL?) throws -> URL {
        guard !uid.isEmpty else { throw CocoaError(.fileWriteInvalidFileName) }
        let root = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Offzone/Purchases", isDirectory: true)
        let name = SHA256.hash(data: Data(uid.utf8)).map { String(format: "%02x", $0) }.joined()
        return root.appendingPathComponent("pending-\(name).txt")
    }
}
