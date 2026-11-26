package com.exchip.offzone

import android.content.Context
import kotlin.math.ceil

/**
 * Grants 14 days of full free starter access from first launch before transitioning to Offzone Pro.
 */
object StarterAccess {
    const val DURATION_DAYS = 14
    const val DAY_MS = 86_400_000L
    const val DURATION_MS = DURATION_DAYS * DAY_MS
    const val REMINDER_BANNER_THRESHOLD_DAYS = 3
    private const val PREFS_NAME = "starter_access"
    private const val KEY_FIRST_LAUNCH_MS = "first_launch_ms"

    /** Debug-only override for tests. Ignored in release builds. */
    @Volatile
    internal var debugExpiredOverride: Boolean? = null

    fun ensureStarted(context: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getLong(KEY_FIRST_LAUNCH_MS, 0L)
        if (existing > 0L) return existing
        prefs.edit().putLong(KEY_FIRST_LAUNCH_MS, nowMs).apply()
        return nowMs
    }

    fun remainingDays(firstLaunchMs: Long, nowMs: Long): Int {
        val elapsed = (nowMs - firstLaunchMs).coerceAtLeast(0L)
        if (elapsed >= DURATION_MS) return 0
        return ceil((DURATION_MS - elapsed).toDouble() / DAY_MS.toDouble()).toInt().coerceIn(0, DURATION_DAYS)
    }

    fun isActive(firstLaunchMs: Long, nowMs: Long): Boolean = remainingDays(firstLaunchMs, nowMs) > 0

    fun remainingDays(context: Context, nowMs: Long = System.currentTimeMillis()): Int {
        if (BuildConfig.DEBUG && debugExpiredOverride == true) return 0
        val started = ensureStarted(context, nowMs)
        return remainingDays(started, nowMs)
    }

    fun isActive(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean =
        remainingDays(context, nowMs) > 0

    fun showsExpiryReminderBanner(hasProAccess: Boolean, remainingDays: Int): Boolean =
        !hasProAccess && remainingDays in 1..REMINDER_BANNER_THRESHOLD_DAYS
}

data class CleanFeedback(val category: String, val message: String, val contact: String?)

object FeedbackValidation {
    const val MIN_MESSAGE_LENGTH = 3
    const val MAX_MESSAGE_LENGTH = 2000
    const val MAX_CONTACT_LENGTH = 254
    val CATEGORIES = setOf("bug", "feature", "general")

    fun sanitize(category: String, message: String, contact: String): CleanFeedback? {
        if (category !in CATEGORIES) return null
        val cleanMessage = message.trim()
        if (cleanMessage.length !in MIN_MESSAGE_LENGTH..MAX_MESSAGE_LENGTH) return null
        val cleanContact = contact.trim()
        if (cleanContact.length > MAX_CONTACT_LENGTH) return null
        return CleanFeedback(category, cleanMessage, cleanContact.ifEmpty { null })
    }
}

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
 * Pure routing rules for the starter-access + paywall flow:
 * - New users get 14-day free starter access: Welcome -> first rule & accessibility setup -> Home.
 * - After 14 days without Pro, unsubscribed users cannot reach Home, Settings, Rule Editor, Quick Focus, or Journal,
 *   while an active focus session always exposes free Restore access over the paywall, and Account/Feedback stay reachable.
 */
internal object HardPaywallFlow {
    const val ROUTE_WELCOME = "welcome"
    const val ROUTE_PAYWALL = "paywall"
    const val ROUTE_ACCOUNT = "account"
    const val ROUTE_FEEDBACK = "feedback"
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
        starterAccessActive: Boolean = false,
    ): String {
        val hasAppAccess = hasProAccess || starterAccessActive
        return when {
            hasPreviewPaywall -> ROUTE_PAYWALL
            !hasAppAccess && hasActiveSession -> ROUTE_PAYWALL
            !hasProAccess && needsFirstRule(onboardingCompleted, hasRules, storageError) -> ROUTE_WELCOME
            !hasAppAccess -> ROUTE_PAYWALL
            needsFirstRule(onboardingCompleted, hasRules, storageError) -> ROUTE_EDIT
            else -> ROUTE_HOME
        }
    }

    /** Guards every route against expired unsubscribed entry while keeping active-session restore & feedback reachable. */
    fun resolveRoute(
        requestedRoute: String,
        hasProAccess: Boolean,
        hasActiveSession: Boolean,
        onboardingCompleted: Boolean,
        hasRules: Boolean,
        storageError: Boolean,
        hasPreviewPaywall: Boolean,
        starterAccessActive: Boolean = false,
    ): String {
        if (hasPreviewPaywall && requestedRoute in setOf(ROUTE_PAYWALL, ROUTE_ACCOUNT, ROUTE_FEEDBACK)) {
            return requestedRoute
        }
        val hasAppAccess = hasProAccess || starterAccessActive
        if (!hasAppAccess) {
            if (hasActiveSession) {
                return if (requestedRoute in setOf(ROUTE_ACCOUNT, ROUTE_FEEDBACK)) requestedRoute else ROUTE_PAYWALL
            }
            return when (requestedRoute) {
                ROUTE_WELCOME -> if (needsFirstRule(onboardingCompleted, hasRules, storageError)) ROUTE_WELCOME else ROUTE_PAYWALL
                ROUTE_PAYWALL, ROUTE_ACCOUNT, ROUTE_FEEDBACK -> requestedRoute
                else -> ROUTE_PAYWALL
            }
        }
        return requestedRoute
    }

    /** Destination immediately after Pro/starter access unlocks setup. */
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

    /** Safe destination when closing AccountScreen. Never enters gated screens without Pro or active starter access. */
    fun accountBackRoute(
        returnRoute: String,
        hasProAccess: Boolean,
        hasActiveSession: Boolean,
        onboardingCompleted: Boolean,
        hasRules: Boolean,
        storageError: Boolean,
        starterAccessActive: Boolean = false,
    ): String {
        if (hasProAccess || starterAccessActive) return returnRoute
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
