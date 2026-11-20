package com.exchip.offzone

import org.junit.Assert.*
import org.junit.Test

class AccountIdentityTest {
    @Test fun identityAndExpiryNeverLeakAcrossAccounts() {
        assertFalse(AccountIdentity.verified(setOf("password"), false))
        assertTrue(AccountIdentity.verified(setOf("google.com"), false))
        assertTrue(AccountIdentity.verified(setOf("apple.com", "password"), false))
        assertTrue(AccountIdentity.verified(setOf("password"), true))
        assertFalse(AccountIdentity.accepts("B", "A", 2, 1))
        assertFalse(AccountIdentity.accepts("A", "A", 3, 1)) // A -> B -> A still rejects old callback.
        assertFalse(AccountIdentity.accepts(null, null, 1, 1))
        assertTrue(AccountIdentity.accepts("A", "A", 3, 3))
        assertFalse(AccountIdentity.proAccess("B", "A", true, null, 100))
        assertFalse(AccountIdentity.proAccess(null, "A", true, null, 100))
        assertFalse(AccountIdentity.proAccess("A", "A", true, 100, 100))
        assertFalse(AccountIdentity.proAccess("A", "A", false, 101, 100))
        assertTrue(AccountIdentity.proAccess("A", "A", true, 101, 100))
        assertTrue(AccountIdentity.sessionID("a".repeat(64)))
        assertFalse(AccountIdentity.sessionID("A".repeat(64)))
        assertFalse(AccountIdentity.sessionID("a".repeat(63)))
    }

    @Test fun entitlementActiveAllowsPlaySandboxInRelease() {
        // Release production entitlement -> active
        assertTrue(AccountIdentity.entitlementActive(
            verificationFailed = false, hasEntitlement = true, isDebug = false, isSandbox = false, isPlayStore = true,
        ))
        assertTrue(AccountIdentity.entitlementActive(
            verificationFailed = false, hasEntitlement = true, isDebug = false, isSandbox = false, isPlayStore = false,
        ))
        // Release Play Store sandbox (Play license tester) -> active
        assertTrue(AccountIdentity.entitlementActive(
            verificationFailed = false, hasEntitlement = true, isDebug = false, isSandbox = true, isPlayStore = true,
        ))
        // Release non-Play sandbox (e.g. iOS App Store sandbox) -> inactive
        assertFalse(AccountIdentity.entitlementActive(
            verificationFailed = false, hasEntitlement = true, isDebug = false, isSandbox = true, isPlayStore = false,
        ))
        // Debug non-Play sandbox -> active
        assertTrue(AccountIdentity.entitlementActive(
            verificationFailed = false, hasEntitlement = true, isDebug = true, isSandbox = true, isPlayStore = false,
        ))
        // Verification failed -> inactive across all build/store combinations
        assertFalse(AccountIdentity.entitlementActive(
            verificationFailed = true, hasEntitlement = true, isDebug = false, isSandbox = false, isPlayStore = true,
        ))
        assertFalse(AccountIdentity.entitlementActive(
            verificationFailed = true, hasEntitlement = true, isDebug = false, isSandbox = true, isPlayStore = true,
        ))
        assertFalse(AccountIdentity.entitlementActive(
            verificationFailed = true, hasEntitlement = true, isDebug = true, isSandbox = true, isPlayStore = false,
        ))
        // Missing entitlement -> inactive across all build/store combinations
        assertFalse(AccountIdentity.entitlementActive(
            verificationFailed = false, hasEntitlement = false, isDebug = false, isSandbox = false, isPlayStore = true,
        ))
        assertFalse(AccountIdentity.entitlementActive(
            verificationFailed = false, hasEntitlement = false, isDebug = false, isSandbox = true, isPlayStore = true,
        ))
        assertFalse(AccountIdentity.entitlementActive(
            verificationFailed = false, hasEntitlement = false, isDebug = true, isSandbox = true, isPlayStore = false,
        ))
    }
}
