package com.exchip.offzone

import org.junit.Assert.*
import org.junit.Test

class ProAccessGateTest {
    @Test
    fun proActionsRequireAccessWhileActiveRestoreIsNeverGated() {
        assertFalse(ProAccessGate.canActivateRule(false))
        assertTrue(ProAccessGate.canActivateRule(true))

        assertFalse(ProAccessGate.canStartFocus(false))
        assertTrue(ProAccessGate.canStartFocus(true))

        assertFalse(ProAccessGate.canQuickFocus(false))
        assertTrue(ProAccessGate.canQuickFocus(true))

        // Safety invariant: active session restore always depends strictly on having an active session.
        assertTrue(ProAccessGate.canRestoreActiveSession(true))
        assertFalse(ProAccessGate.canRestoreActiveSession(false))
    }

    @Test
    fun releaseModeIgnoresDebugOverrideWhileDebugAppliesIt() {
        try {
            assertNull(ProAccessGate.debugProOverride)

            // Release builds never honor any debug override.
            assertFalse(ProAccessGate.effectiveProAccess(actualProAccess = false, isDebug = false, overrideValue = true))
            assertTrue(ProAccessGate.effectiveProAccess(actualProAccess = true, isDebug = false, overrideValue = false))
            assertFalse(ProAccessGate.effectiveProAccess(actualProAccess = false, isDebug = false, overrideValue = null))

            // Debug builds honor non-null override values and fall back to actual access when null.
            ProAccessGate.debugProOverride = true
            assertTrue(ProAccessGate.effectiveProAccess(actualProAccess = false, isDebug = true))
            ProAccessGate.debugProOverride = false
            assertFalse(ProAccessGate.effectiveProAccess(actualProAccess = true, isDebug = true))
            ProAccessGate.debugProOverride = null
            assertTrue(ProAccessGate.effectiveProAccess(actualProAccess = true, isDebug = true))
            assertFalse(ProAccessGate.effectiveProAccess(actualProAccess = false, isDebug = true))
        } finally {
            ProAccessGate.debugProOverride = null
        }
    }

    @Test
    fun hardPaywallFlowRoutesNewAndExistingUsersWithoutLeakingProScreens() {
        // Brand-new unsubscribed user starts on Welcome.
        assertEquals(
            HardPaywallFlow.ROUTE_WELCOME,
            HardPaywallFlow.initialRoute(
                hasProAccess = false,
                hasActiveSession = false,
                onboardingCompleted = false,
                hasRules = false,
                storageError = false,
                hasPreviewPaywall = false,
            ),
        )

        // Existing unsubscribed user (completed onboarding or saved rule) is covered by Paywall.
        assertEquals(
            HardPaywallFlow.ROUTE_PAYWALL,
            HardPaywallFlow.initialRoute(
                hasProAccess = false,
                hasActiveSession = false,
                onboardingCompleted = true,
                hasRules = false,
                storageError = false,
                hasPreviewPaywall = false,
            ),
        )
        assertEquals(
            HardPaywallFlow.ROUTE_PAYWALL,
            HardPaywallFlow.initialRoute(
                hasProAccess = false,
                hasActiveSession = false,
                onboardingCompleted = false,
                hasRules = true,
                storageError = false,
                hasPreviewPaywall = false,
            ),
        )

        // Non-Pro user can never reach Home, Settings, Rule Editor, Setup, Quick Focus, or Journal.
        val gatedRoutes = listOf(
            HardPaywallFlow.ROUTE_HOME,
            HardPaywallFlow.ROUTE_SETTINGS,
            HardPaywallFlow.ROUTE_EDIT,
            HardPaywallFlow.ROUTE_SETUP,
            HardPaywallFlow.ROUTE_QUICK,
            HardPaywallFlow.ROUTE_JOURNAL,
        )
        gatedRoutes.forEach { blocked ->
            assertEquals(
                "Route $blocked must redirect to paywall without Pro",
                HardPaywallFlow.ROUTE_PAYWALL,
                HardPaywallFlow.resolveRoute(
                    requestedRoute = blocked,
                    hasProAccess = false,
                    hasActiveSession = false,
                    onboardingCompleted = false,
                    hasRules = false,
                    storageError = false,
                    hasPreviewPaywall = false,
                ),
            )
        }

        // Account stays reachable without Pro so signed-in users can sign out or delete their account.
        assertEquals(
            HardPaywallFlow.ROUTE_ACCOUNT,
            HardPaywallFlow.resolveRoute(
                requestedRoute = HardPaywallFlow.ROUTE_ACCOUNT,
                hasProAccess = false,
                hasActiveSession = false,
                onboardingCompleted = true,
                hasRules = true,
                storageError = false,
                hasPreviewPaywall = false,
            ),
        )
        // Leaving Account without Pro never bounces into Home or Settings.
        assertEquals(
            HardPaywallFlow.ROUTE_PAYWALL,
            HardPaywallFlow.accountBackRoute(
                returnRoute = HardPaywallFlow.ROUTE_HOME,
                hasProAccess = false,
                hasActiveSession = false,
                onboardingCompleted = true,
                hasRules = true,
                storageError = false,
            ),
        )
        assertEquals(
            HardPaywallFlow.ROUTE_WELCOME,
            HardPaywallFlow.accountBackRoute(
                returnRoute = HardPaywallFlow.ROUTE_WELCOME,
                hasProAccess = false,
                hasActiveSession = false,
                onboardingCompleted = false,
                hasRules = false,
                storageError = false,
            ),
        )

        // Once Pro is active: brand-new user creates first rule -> accessibility setup -> Home.
        assertEquals(
            HardPaywallFlow.ROUTE_EDIT,
            HardPaywallFlow.postProRoute(
                onboardingCompleted = false,
                hasRules = false,
                storageError = false,
                accessibilityConnected = false,
            ),
        )
        assertEquals(
            HardPaywallFlow.ROUTE_SETUP,
            HardPaywallFlow.afterRuleSavedRoute(firstTime = true, accessibilityConnected = false),
        )
        assertEquals(
            HardPaywallFlow.ROUTE_HOME,
            HardPaywallFlow.afterRuleSavedRoute(firstTime = true, accessibilityConnected = true),
        )
        assertEquals(
            HardPaywallFlow.ROUTE_HOME,
            HardPaywallFlow.afterRuleSavedRoute(firstTime = false, accessibilityConnected = false),
        )
        // Existing subscriber with saved rules lands straight on Home.
        assertEquals(
            HardPaywallFlow.ROUTE_HOME,
            HardPaywallFlow.postProRoute(
                onboardingCompleted = true,
                hasRules = true,
                storageError = false,
                accessibilityConnected = false,
            ),
        )
        // Debug paywall preview always launches on Paywall.
        assertEquals(
            HardPaywallFlow.ROUTE_PAYWALL,
            HardPaywallFlow.initialRoute(
                hasProAccess = true,
                hasActiveSession = false,
                onboardingCompleted = true,
                hasRules = true,
                storageError = false,
                hasPreviewPaywall = true,
            ),
        )
    }

    @Test
    fun activeSessionWithoutProShowsFreeRestoreOverHardPaywall() {
        // An active focus session without Pro stays on Paywall (even for fresh onboarding) with Restore visible.
        assertEquals(
            HardPaywallFlow.ROUTE_PAYWALL,
            HardPaywallFlow.initialRoute(
                hasProAccess = false,
                hasActiveSession = true,
                onboardingCompleted = false,
                hasRules = false,
                storageError = false,
                hasPreviewPaywall = false,
            ),
        )
        assertEquals(
            HardPaywallFlow.ROUTE_PAYWALL,
            HardPaywallFlow.resolveRoute(
                requestedRoute = HardPaywallFlow.ROUTE_WELCOME,
                hasProAccess = false,
                hasActiveSession = true,
                onboardingCompleted = false,
                hasRules = false,
                storageError = false,
                hasPreviewPaywall = false,
            ),
        )
        assertEquals(
            HardPaywallFlow.ROUTE_PAYWALL,
            HardPaywallFlow.resolveRoute(
                requestedRoute = HardPaywallFlow.ROUTE_HOME,
                hasProAccess = false,
                hasActiveSession = true,
                onboardingCompleted = true,
                hasRules = true,
                storageError = false,
                hasPreviewPaywall = false,
            ),
        )
        assertTrue(HardPaywallFlow.showRestoreOverPaywall(hasProAccess = false, hasActiveSession = true))
        assertFalse(HardPaywallFlow.showRestoreOverPaywall(hasProAccess = false, hasActiveSession = false))
        assertFalse(HardPaywallFlow.showRestoreOverPaywall(hasProAccess = true, hasActiveSession = true))
        assertFalse(HardPaywallFlow.showRestoreOverPaywall(hasProAccess = true, hasActiveSession = false))

        // Leaving Account during an active unsubscribed session returns to Paywall so Restore stays one tap away.
        assertEquals(
            HardPaywallFlow.ROUTE_PAYWALL,
            HardPaywallFlow.accountBackRoute(
                returnRoute = HardPaywallFlow.ROUTE_WELCOME,
                hasProAccess = false,
                hasActiveSession = true,
                onboardingCompleted = false,
                hasRules = false,
                storageError = false,
            ),
        )
    }

    @Test
    fun starterAccessGrantsFourteenDaysThenTransitionsToPaywall() {
        val day0 = 1_700_000_000_000L
        assertEquals(14, StarterAccess.remainingDays(day0, day0))
        assertTrue(StarterAccess.isActive(day0, day0))
        assertFalse(StarterAccess.showsExpiryReminderBanner(hasProAccess = false, remainingDays = 14))

        val day11 = day0 + 11 * StarterAccess.DAY_MS + 60_000L
        assertEquals(3, StarterAccess.remainingDays(day0, day11))
        assertTrue(StarterAccess.showsExpiryReminderBanner(hasProAccess = false, remainingDays = 3))
        assertFalse(StarterAccess.showsExpiryReminderBanner(hasProAccess = true, remainingDays = 3))

        val day14 = day0 + 14 * StarterAccess.DAY_MS
        assertEquals(0, StarterAccess.remainingDays(day0, day14))
        assertFalse(StarterAccess.isActive(day0, day14))

        // Fresh user during active starter sees Welcome first, then can reach Home/Edit/Quick/Journal without paywall.
        assertEquals(
            HardPaywallFlow.ROUTE_WELCOME,
            HardPaywallFlow.initialRoute(
                hasProAccess = false,
                hasActiveSession = false,
                onboardingCompleted = false,
                hasRules = false,
                storageError = false,
                hasPreviewPaywall = false,
                starterAccessActive = true,
            ),
        )
        assertEquals(
            HardPaywallFlow.ROUTE_HOME,
            HardPaywallFlow.initialRoute(
                hasProAccess = false,
                hasActiveSession = false,
                onboardingCompleted = true,
                hasRules = true,
                storageError = false,
                hasPreviewPaywall = false,
                starterAccessActive = true,
            ),
        )
        assertEquals(
            HardPaywallFlow.ROUTE_QUICK,
            HardPaywallFlow.resolveRoute(
                requestedRoute = HardPaywallFlow.ROUTE_QUICK,
                hasProAccess = false,
                hasActiveSession = false,
                onboardingCompleted = true,
                hasRules = true,
                storageError = false,
                hasPreviewPaywall = false,
                starterAccessActive = true,
            ),
        )
        // Feedback stays reachable even after starter expiry without Pro.
        assertEquals(
            HardPaywallFlow.ROUTE_FEEDBACK,
            HardPaywallFlow.resolveRoute(
                requestedRoute = HardPaywallFlow.ROUTE_FEEDBACK,
                hasProAccess = false,
                hasActiveSession = false,
                onboardingCompleted = true,
                hasRules = true,
                storageError = false,
                hasPreviewPaywall = false,
                starterAccessActive = false,
            ),
        )
    }

    @Test
    fun feedbackValidationTrimsAndRejectsInvalidInputs() {
        assertNull(FeedbackValidation.sanitize("unknown", "hello world", ""))
        assertNull(FeedbackValidation.sanitize("bug", "  ", ""))
        assertNull(FeedbackValidation.sanitize("bug", "ab", ""))
        assertNull(FeedbackValidation.sanitize("bug", "a".repeat(2001), ""))
        assertNull(FeedbackValidation.sanitize("bug", "Valid message", "x".repeat(255)))

        val clean = FeedbackValidation.sanitize("feature", "  Add custom durations  ", "  ")
        assertNotNull(clean)
        assertEquals("feature", clean!!.category)
        assertEquals("Add custom durations", clean.message)
        assertNull(clean.contact)

        val withContact = FeedbackValidation.sanitize("bug", "Crash on pause", " user@example.com ")
        assertEquals("user@example.com", withContact?.contact)
    }
}
