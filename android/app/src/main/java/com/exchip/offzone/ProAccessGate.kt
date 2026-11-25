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

/**
 * Pure routing rules for the hard-paywall flow:
 * Welcome -> Paywall -> (once Pro) first rule & accessibility setup -> Home.
 * Unsubscribed users cannot reach Home, Settings, Rule Editor, Quick Focus, or Journal,
 * while an active focus session always exposes free Restore access over the paywall.
 */
internal object HardPaywallFlow {
    const val ROUTE_WELCOME = "welcome"
    const val ROUTE_PAYWALL = "paywall"
    const val ROUTE_ACCOUNT = "account"
    const val ROUTE_EDIT = "edit"
    const val ROUTE_SETUP = "setup"
    const val ROUTE_HOME = "home"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_QUICK = "quick"
    const val ROUTE_JOURNAL = "journal"

    /** True when the device has no completed onboarding, no saved rules, and healthy rule storage. */
    fun needsFirstRule(onboardingCompleted: Boolean, hasRules: Boolean, storageError: Boolean): Boolean =
        !onboardingCompleted && !hasRules && !storageError

    /** Initial route on activity launch. */
    fun initialRoute(
        hasProAccess: Boolean,
        hasActiveSession: Boolean,
        onboardingCompleted: Boolean,
        hasRules: Boolean,
        storageError: Boolean,
        hasPreviewPaywall: Boolean,
    ): String = when {
        hasPreviewPaywall -> ROUTE_PAYWALL
        !hasProAccess && hasActiveSession -> ROUTE_PAYWALL
        !hasProAccess && needsFirstRule(onboardingCompleted, hasRules, storageError) -> ROUTE_WELCOME
        !hasProAccess -> ROUTE_PAYWALL
        needsFirstRule(onboardingCompleted, hasRules, storageError) -> ROUTE_EDIT
        else -> ROUTE_HOME
    }

    /** Guards every route against unsubscribed entry while keeping active-session restore reachable. */
    fun resolveRoute(
        requestedRoute: String,
        hasProAccess: Boolean,
        hasActiveSession: Boolean,
        onboardingCompleted: Boolean,
        hasRules: Boolean,
        storageError: Boolean,
        hasPreviewPaywall: Boolean,
    ): String {
        if (hasPreviewPaywall && requestedRoute in setOf(ROUTE_PAYWALL, ROUTE_ACCOUNT)) {
            return requestedRoute
        }
        if (!hasProAccess) {
            if (hasActiveSession) {
                return if (requestedRoute == ROUTE_ACCOUNT) ROUTE_ACCOUNT else ROUTE_PAYWALL
            }
            return when (requestedRoute) {
                ROUTE_WELCOME -> if (needsFirstRule(onboardingCompleted, hasRules, storageError)) ROUTE_WELCOME else ROUTE_PAYWALL
                ROUTE_PAYWALL, ROUTE_ACCOUNT -> requestedRoute
                else -> ROUTE_PAYWALL
            }
        }
        return requestedRoute
    }

    /** Destination immediately after Pro access is granted. */
    fun postProRoute(
        onboardingCompleted: Boolean,
        hasRules: Boolean,
        storageError: Boolean,
        accessibilityConnected: Boolean,
    ): String = when {
        needsFirstRule(onboardingCompleted, hasRules, storageError) -> ROUTE_EDIT
        !onboardingCompleted && !accessibilityConnected -> ROUTE_SETUP
        else -> ROUTE_HOME
    }

    /** Destination after saving a rule in RuleEditorScreen. */
    fun afterRuleSavedRoute(firstTime: Boolean, accessibilityConnected: Boolean): String =
        if (firstTime && !accessibilityConnected) ROUTE_SETUP else ROUTE_HOME

    /** Safe destination when closing AccountScreen. Never enters Pro-only screens without Pro. */
    fun accountBackRoute(
        returnRoute: String,
        hasProAccess: Boolean,
        hasActiveSession: Boolean,
        onboardingCompleted: Boolean,
        hasRules: Boolean,
        storageError: Boolean,
    ): String {
        if (hasProAccess) return returnRoute
        if (hasActiveSession) return ROUTE_PAYWALL
        return if (returnRoute == ROUTE_WELCOME && needsFirstRule(onboardingCompleted, hasRules, storageError)) {
            ROUTE_WELCOME
        } else {
            ROUTE_PAYWALL
        }
    }

    /** Whether the free active-session Restore access card must appear on top of the hard paywall. */
    fun showRestoreOverPaywall(hasProAccess: Boolean, hasActiveSession: Boolean): Boolean =
        !hasProAccess && ProAccessGate.canRestoreActiveSession(hasActiveSession)
}
