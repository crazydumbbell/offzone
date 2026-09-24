import XCTest
@testable import RoomDNS

final class AccountStoreTests: XCTestCase {
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
}
