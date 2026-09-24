package com.exchip.offzone

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object EngineNotifications {
    const val MONITOR_ID = 410
    private fun manager(context: Context): NotificationManager = context.getSystemService(NotificationManager::class.java).also {
        it.createNotificationChannel(NotificationChannel("focus_monitor", context.getString(R.string.engine_monitor_channel), NotificationManager.IMPORTANCE_LOW))
        it.createNotificationChannel(NotificationChannel("focus_events", context.getString(R.string.engine_event_channel), NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun open(context: Context) = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    fun monitor(context: Context): Notification {
        manager(context)
        val restore = PendingIntent.getService(context, 1, Intent(context, PlaceMonitor::class.java).setAction("restore"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context, "focus_monitor").setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(context.getString(R.string.engine_monitor_title))
            .setContentText(context.getString(R.string.engine_monitor_body)).setContentIntent(open(context))
            .setOngoing(true).addAction(Notification.Action.Builder(null, context.getString(R.string.restore), restore).build()).build()
    }
    fun send(context: Context, message: Int) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        runCatching { manager(context).notify(411, Notification.Builder(context, "focus_events")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle("Offzone")
            .setContentText(context.getString(message)).setContentIntent(open(context)).setAutoCancel(true).build()) }
    }
}
