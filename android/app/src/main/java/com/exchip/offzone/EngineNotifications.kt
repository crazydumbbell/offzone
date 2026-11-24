package com.exchip.offzone

import android.Manifest
import android.app.LocaleManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

object EngineNotifications {
    const val MONITOR_ID = 410
    const val EVENT_ID = 411

    /** Returns a configuration context matching the app's active locale on API 33+. */
    internal fun localizedContext(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                val config = Configuration(context.resources.configuration).apply { setLocales(locales) }
                return context.createConfigurationContext(config)
            }
        }
        return context
    }

    /** Refreshes notification channels using the active app locale. */
    fun refreshChannels(context: Context) {
        manager(localizedContext(context))
    }

    private fun manager(context: Context): NotificationManager = context.getSystemService(NotificationManager::class.java).also {
        it.createNotificationChannel(NotificationChannel("focus_monitor", context.getString(R.string.engine_monitor_channel), NotificationManager.IMPORTANCE_LOW))
        it.createNotificationChannel(NotificationChannel("focus_events", context.getString(R.string.engine_event_channel), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun open(context: Context) = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun monitor(context: Context): Notification {
        val localized = localizedContext(context)
        manager(localized)
        val restore = PendingIntent.getService(context, 1, Intent(context, PlaceMonitor::class.java).setAction("restore"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(localized, "focus_monitor").setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(localized.getString(R.string.engine_monitor_title))
            .setContentText(localized.getString(R.string.engine_monitor_body)).setContentIntent(open(context))
            .setOngoing(true).addAction(Notification.Action.Builder(null, localized.getString(R.string.restore), restore).build()).build()
    }

    fun send(context: Context, message: Int) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val localized = localizedContext(context)
        runCatching {
            manager(localized).notify(EVENT_ID, Notification.Builder(localized, "focus_events")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle(localized.getString(R.string.app_name))
                .setContentText(localized.getString(message)).setContentIntent(open(context)).setAutoCancel(true).build())
        }
    }
}
