import XCTest
import FirebaseAuth
import RevenueCat
@testable import RoomDNS

final class AccountStoreTests: XCTestCase {
    func testJournalDraftGuardKeepsOtherUnsavedEditorDirtyAfterSavingOne() {
        let plan = ProJournalData.Plan(intention: "Read", context: "personal", weekdays: [2])
        let reflection = ProJournalData.Reflection(note: "A quiet hour", outcome: "Kept my intention")
        var baseline = JournalDraftBaseline(plan: plan, reflection: reflection)
        var editedPlan = plan
        editedPlan.weekdays.insert(3)
        var editedReflection = reflection
        editedReflection.note = "New unsaved reflection"
        XCTAssertFalse(baseline.hasChanges(plan: plan, reflection: reflection))
        XCTAssertTrue(baseline.hasChanges(plan: editedPlan, reflection: reflection))
        baseline.plan = editedPlan // Saving the plan must not discard the reflection warning.
        XCTAssertTrue(baseline.hasChanges(plan: editedPlan, reflection: editedReflection))
        baseline.reflection = editedReflection
        XCTAssertFalse(baseline.hasChanges(plan: editedPlan, reflection: editedReflection))
        editedPlan.context = "work"
        XCTAssertTrue(baseline.hasChanges(plan: editedPlan, reflection: editedReflection))
    }

    func testPendingUnlockRequestIsDurableUntilExplicitCompletion() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("offzone-pending-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let uid = "firebase-user"
        let sessionID = String(repeating: "a", count: 64)
        XCTAssertNil(try UnlockPassPendingStore.load(uid: uid, directory: directory))
        try UnlockPassPendingStore.save(sessionID: sessionID, uid: uid, directory: directory)
        XCTAssertEqual(try UnlockPassPendingStore.load(uid: uid, directory: directory), sessionID)
        try UnlockPassPendingStore.remove(uid: uid, directory: directory)
        XCTAssertNil(try UnlockPassPendingStore.load(uid: uid, directory: directory))
    }

    @MainActor
    func testProAccessRequiresCurrentIdentityAndUnexpiredVerifiedEntitlement() {
        let expiry = Date(timeIntervalSince1970: 100)
        func allowed(signedIn: Bool = true, current: String? = "a", owner: String? = "a",
                     verified: Bool = true, expiration: Date? = Date(timeIntervalSince1970: 100),
                     now: Date = Date(timeIntervalSince1970: 99)) -> Bool {
            AccountStore.proAccessIsValid(signedIn: signedIn, currentUID: current,
                entitlementUID: owner, verifiedActive: verified, expirationDate: expiration, now: now)
        }
        XCTAssertTrue(allowed())
        XCTAssertFalse(allowed(now: expiry))
        XCTAssertFalse(allowed(now: expiry.addingTimeInterval(1)))
        XCTAssertFalse(allowed(signedIn: false))
        XCTAssertFalse(allowed(current: nil))
        XCTAssertFalse(allowed(current: "b"))
        XCTAssertFalse(allowed(owner: nil))
        XCTAssertFalse(allowed(verified: false))
        XCTAssertTrue(allowed(expiration: nil)) // Verified lifetime/promotional entitlement.
    }

    @MainActor
    func testPasswordOnlyIdentityRequiresVerificationAndEmailValidationPreservesAddress() throws {
        XCTAssertFalse(AccountStore.identityIsVerified(providerIDs: ["password"], emailVerified: false))
        XCTAssertTrue(AccountStore.identityIsVerified(providerIDs: ["password"], emailVerified: true))
        XCTAssertFalse(AccountStore.identityIsVerified(providerIDs: [], emailVerified: true))
        XCTAssertTrue(AccountStore.identityIsVerified(providerIDs: ["apple.com"], emailVerified: false))
        XCTAssertTrue(AccountStore.identityIsVerified(providerIDs: ["google.com", "password"], emailVerified: false))
        XCTAssertEqual(try AccountStore.validatedEmail("  person+offzone@example.com\n"), "person+offzone@example.com")
        for invalid in ["", "person", "@example.com", "a@@example.com", "a@.com", "a@example.", "a b@example.com"] {
            XCTAssertThrowsError(try AccountStore.validatedEmail(invalid))
        }
    }

    @MainActor
    func testMissingConfigurationStaysFreeAndOfferRequiresCompleteApproval() async {
        let account = AccountStore(bundle: Bundle(for: Self.self))
        XCTAssertFalse(account.isConfigured)
        XCTAssertFalse(account.isSignedIn)
        XCTAssertFalse(account.purchasesConfigured)
        XCTAssertFalse(account.canShowOffer)
        XCTAssertFalse(account.unlockPassEnabled)
        XCTAssertFalse(account.canPurchaseUnlockPass)
        XCTAssertFalse(account.canUseUnlockPass)
        await account.refresh()
        await account.loadOfferings()
        await account.saveGoal("work")
        await account.signInWithEmail(email: "person@example.com", password: "password")
        await account.createEmailAccount(email: "person@example.com", password: "password")
        await account.linkEmailAccount(email: "person@example.com", password: "password")
        await account.sendEmailVerification()
        await account.reloadEmailVerification()
        await account.sendPasswordReset(email: "person@example.com")
        await account.signInWithGoogle()
        await account.deleteAccountWithPassword("password")
        await account.deleteAccountWithGoogle()
        XCTAssertFalse(account.googleSignInConfigured)
        XCTAssertFalse(account.needsEmailVerification)
        XCTAssertTrue(account.providerIDs.isEmpty)
        XCTAssertFalse(account.hasProAccess)
        XCTAssertFalse(account.isPro)
        XCTAssertTrue(account.packages.isEmpty)

        let legal = URL(string: "https://example.com/legal")!
        XCTAssertFalse(AccountStore.offerIsReady(ready: false, offeringID: "main", entitlementID: "pro", benefits: ["Implemented benefit"], termsURL: legal, privacyURL: legal))
        XCTAssertFalse(AccountStore.offerIsReady(ready: true, offeringID: "", entitlementID: "pro", benefits: ["Implemented benefit"], termsURL: legal, privacyURL: legal))
        XCTAssertFalse(AccountStore.offerIsReady(ready: true, offeringID: "main", entitlementID: "", benefits: ["Implemented benefit"], termsURL: legal, privacyURL: legal))
        XCTAssertFalse(AccountStore.offerIsReady(ready: true, offeringID: "main", entitlementID: "pro", benefits: [], termsURL: legal, privacyURL: legal))
        XCTAssertFalse(AccountStore.offerIsReady(ready: true, offeringID: "main", entitlementID: "pro", benefits: ["Implemented benefit"], termsURL: nil, privacyURL: legal))
        XCTAssertTrue(AccountStore.offerIsReady(ready: true, offeringID: "main", entitlementID: "pro", benefits: ["Implemented benefit"], termsURL: legal, privacyURL: legal))
        XCTAssertFalse(AccountStore.unlockIsReady(ready: false, productID: "unlock"))
        XCTAssertFalse(AccountStore.unlockIsReady(ready: true, productID: ""))
        XCTAssertTrue(AccountStore.unlockIsReady(ready: true, productID: "unlock"))
    }

    func testSDKAndStoreErrorsMapToCleanLocalizedMessages() {
        let networkError = URLError(.notConnectedToInternet)
        let authMessage = AccountStore.localizedAuthError(networkError)
        XCTAssertFalse(authMessage.contains("NSURLErrorDomain"))
        XCTAssertFalse(authMessage.isEmpty)

        let wrongPassword = NSError(domain: AuthErrorDomain, code: AuthErrorCode.wrongPassword.rawValue)
        let authPasswordMessage = AccountStore.localizedAuthError(wrongPassword)
        XCTAssertFalse(authPasswordMessage.contains("FIRAuthErrorDomain"))
        XCTAssertFalse(authPasswordMessage.isEmpty)

        let storeProblem = ErrorCode.storeProblemError
        let storeMessage = AccountStore.localizedStoreError(storeProblem)
        XCTAssertFalse(storeMessage.contains("RevenueCat"))
        XCTAssertFalse(storeMessage.localizedCaseInsensitiveContains("continue with free"))
        XCTAssertFalse(storeMessage.isEmpty)
    }

    func testJournalDateKeyParsingHandlesValidAndInvalidDates() {
        XCTAssertNotNil(ProJournalStore.date(fromKey: "2026-11-22"))
        XCTAssertNil(ProJournalStore.date(fromKey: "2026-02-30"))
        XCTAssertNil(ProJournalStore.date(fromKey: "not-a-date"))
    }

    func testJournalOutcomeLocalizationKeySeparatesTryAgainWithoutChangingStoredValues() {
        XCTAssertEqual(ProJournalStore.outcomes, ["Kept my intention", "Partly", "Try again"])
        XCTAssertEqual(ProJournalStore.outcomeKey("Kept my intention"), "Kept my intention")
        XCTAssertEqual(ProJournalStore.outcomeKey("Partly"), "Partly")
        XCTAssertEqual(ProJournalStore.outcomeKey("Try again"), "journal_outcome_try_again")
    }

    func testAccountViewPolicyCollapsesSignInWhenProIsActiveAndDeduplicatesStatus() {
        XCTAssertTrue(AccountViewPolicy.showsSignInMethods(hasProAccess: false, signInExpanded: false))
        XCTAssertFalse(AccountViewPolicy.showsSignInMethods(hasProAccess: true, signInExpanded: false))
        XCTAssertTrue(AccountViewPolicy.showsSignInMethods(hasProAccess: true, signInExpanded: true))

        let status = "Your Pro subscription is active."
        XCTAssertFalse(AccountViewPolicy.showsAccountStatusMessage(status, matching: status))
        XCTAssertTrue(AccountViewPolicy.showsAccountStatusMessage("Email verified.", matching: status))
        XCTAssertFalse(AccountViewPolicy.showsAccountStatusMessage(nil, matching: status))
    }

    func testJournalSummaryPolicyHidesZeroTilesWhenEmpty() {
        XCTAssertFalse(JournalSummaryPolicy.shouldShowSummary(planned: 0, reflected: 0, kept: 0))
        XCTAssertTrue(JournalSummaryPolicy.shouldShowSummary(planned: 3, reflected: 0, kept: 0))
        XCTAssertTrue(JournalSummaryPolicy.shouldShowSummary(planned: 0, reflected: 1, kept: 0))
        XCTAssertTrue(JournalSummaryPolicy.shouldShowSummary(planned: 5, reflected: 2, kept: 1))
        XCTAssertFalse(JournalSummaryPolicy.shouldShowCharacterCount(0))
        XCTAssertFalse(JournalSummaryPolicy.shouldShowCharacterCount(1599))
        XCTAssertTrue(JournalSummaryPolicy.shouldShowCharacterCount(1600))
        XCTAssertTrue(JournalSummaryPolicy.shouldShowCharacterCount(2000))
    }
}
