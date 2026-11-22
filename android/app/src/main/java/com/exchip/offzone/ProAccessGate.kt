package com.exchip.offzone

/**
 * Access policy for Pro-gated focus features and the always-free active-session restore invariant.
 */
object ProAccessGate {
    internal const val EXTRA_DEBUG_PRO = "offzone_debug_pro"

    /** Debug-only Pro access override for instrumentation tests. Ignored in release builds. */
    @Volatile
    internal var debugProOverride: Boolean? = null

    /** Returns whether rule activation is permitted for the given Pro status. */
    fun canActivateRule(hasProAccess: Boolean): Boolean = hasProAccess

    /** Returns whether starting a scheduled or place-based focus session is permitted. */
    fun canStartFocus(hasProAccess: Boolean): Boolean = hasProAccess

    /** Returns whether Quick Focus can be opened and started. */
    fun canQuickFocus(hasProAccess: Boolean): Boolean = hasProAccess

    /**
     * Returns whether an active focus session can be ended early for free.
     * Always matches [hasActiveSession] and is never gated by Pro access.
     */
    fun canRestoreActiveSession(hasActiveSession: Boolean): Boolean = hasActiveSession

    /** Resolves effective Pro access, honoring [overrideValue] only when [isDebug] is true. */
    fun effectiveProAccess(
        actualProAccess: Boolean,
        isDebug: Boolean = BuildConfig.DEBUG,
        overrideValue: Boolean? = debugProOverride,
    ): Boolean = if (isDebug && overrideValue != null) overrideValue else actualProAccess
}
