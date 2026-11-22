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
}
