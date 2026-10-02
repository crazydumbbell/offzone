package com.exchip.offzone

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** One local notification, 24 hours before a free trial ends. Scheduled only after a trial purchase the user opted in to (D-49). */
object TrialReminder {
    private const val ACTION = "com.exchip.offzone.TRIAL_REMINDER"
    private const val PREFS = "trial_reminder"
    private const val DAY_MS = 86_400_000L

    /** When to remind for a trial of [trialDays] that starts at [now]; null when the trial is a day or less. */
    fun reminderAt(now: Long, trialDays: Int): Long? = if (trialDays >= 2) now + (trialDays - 1) * DAY_MS else null

    fun schedule(context: Context, trialDays: Int, now: Long = System.currentTimeMillis()) =
        reminderAt(now, trialDays)?.let { scheduleAt(context, it) } ?: false

    fun scheduleAt(context: Context, at: Long): Boolean {
        if (at <= System.currentTimeMillis()) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("at", at).apply()
        // Inexact on purpose: a reminder a few minutes late needs no exact-alarm permission.
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
        return true
    }

    internal fun restore(context: Context) {
        val at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("at", 0L)
        if (at > System.currentTimeMillis()) context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    internal fun post(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("at").apply()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("trial_reminder", context.getString(R.string.trial_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching {
            manager.notify(412, Notification.Builder(context, "trial_reminder").setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle(context.getString(R.string.pro_banner_title)).setContentText(context.getString(R.string.trial_reminder_body))
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 3,
        Intent(context, TrialReminderReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    internal const val FIRE = ACTION
}

class TrialReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TrialReminder.FIRE -> TrialReminder.post(context)
            Intent.ACTION_BOOT_COMPLETED -> TrialReminder.restore(context) // Alarms do not survive a reboot.
        }
    }
}
