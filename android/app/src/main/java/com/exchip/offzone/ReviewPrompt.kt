package com.exchip.offzone

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Throttles Google Play's native In-App Review flow so it only fires after genuine positive
 * moments (completed focus sessions or saved reflections), never on errors or emergency exits.
 */
object ReviewPromptPolicy {
    const val FIRST_PROMPT_MOMENTS = 2
    const val REPEAT_PROMPT_DELTA = 3
    const val COOLDOWN_MILLIS = 60L * 86_400_000L

    private const val PREFS_NAME = "offzone_review_prompt"
    private const val KEY_MOMENTS = "positive_moments"
    private const val KEY_LAST_PROMPTED_AT = "last_prompted_at"
    private const val KEY_LAST_PROMPTED_MOMENTS = "last_prompted_moments"

    fun shouldPrompt(
        momentsCount: Int,
        lastPromptedAtMillis: Long?,
        lastPromptedMoments: Int,
        nowMillis: Long,
    ): Boolean {
        if (momentsCount < FIRST_PROMPT_MOMENTS) return false
        if (lastPromptedAtMillis == null) return true
        if (nowMillis - lastPromptedAtMillis < COOLDOWN_MILLIS) return false
        return momentsCount - lastPromptedMoments >= REPEAT_PROMPT_DELTA
    }

    fun recordPositiveMoment(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val moments = prefs.getInt(KEY_MOMENTS, 0) + 1
        val lastAtRaw = prefs.getLong(KEY_LAST_PROMPTED_AT, 0L)
        val lastPromptedAt = if (lastAtRaw > 0L) lastAtRaw else null
        val lastPromptedMoments = prefs.getInt(KEY_LAST_PROMPTED_MOMENTS, 0)
        val eligible = shouldPrompt(moments, lastPromptedAt, lastPromptedMoments, nowMillis)
        val editor = prefs.edit().putInt(KEY_MOMENTS, moments)
        if (eligible) {
            editor.putLong(KEY_LAST_PROMPTED_AT, nowMillis)
            editor.putInt(KEY_LAST_PROMPTED_MOMENTS, moments)
        }
        editor.apply()
        return eligible
    }
}

object InAppReviewPrompt {
    fun recordPositiveMomentAndRequestIfEligible(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!ReviewPromptPolicy.recordPositiveMoment(activity)) return
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful && !activity.isFinishing && !activity.isDestroyed) {
                    runCatching { manager.launchReviewFlow(activity, task.result) }
                }
            }
        }
    }
}
