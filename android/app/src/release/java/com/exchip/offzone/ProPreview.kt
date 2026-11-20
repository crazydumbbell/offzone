package com.exchip.offzone

import android.content.Context
import android.content.Intent

/** Release twin of the debug preview: no sample plans or prices ship in a store build (D-49). */
internal object ProPreview {
    fun variant(intent: Intent?): String? = null
    fun plans(variant: String?): List<ProPlan>? = null
    fun reminderTest(context: Context, intent: Intent?) {}
}
