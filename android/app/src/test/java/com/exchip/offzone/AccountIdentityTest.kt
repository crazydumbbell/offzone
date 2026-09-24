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
}
