package com.exchip.offzone

import android.content.Context
import android.content.Intent

/** Debug-only sample plans so the whole Pro funnel can be reviewed while sales are off. Release builds ship an empty twin (D-49). */
internal object ProPreview {
    /** `--es offzone_preview_paywall trial|plain|signedout` on the launch intent; `signedout` pretends nobody is signed in, the others pretend someone is. */
    fun variant(intent: Intent?): String? = intent?.getStringExtra("offzone_preview_paywall")

    fun plans(variant: String?): List<ProPlan>? {
        val trial = when (variant) { "trial", "signedout" -> 14; "plain" -> null; else -> return null }
        return listOf(
            ProPlan("preview_monthly", false, "\$4.99", 4_990_000, "USD", trial),
            ProPlan("preview_annual", true, "\$29.99", 29_990_000, "USD", null),
        )
    }

    /** `--ei offzone_preview_reminder_seconds N` fires the trial reminder after N seconds, to check the notification. */
    fun reminderTest(context: Context, intent: Intent?) {
        val seconds = intent?.getIntExtra("offzone_preview_reminder_seconds", 0) ?: 0
        if (seconds > 0) TrialReminder.scheduleAt(context, System.currentTimeMillis() + seconds * 1000L)
    }
}
