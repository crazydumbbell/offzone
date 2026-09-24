package com.exchip.offzone

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

data class SelectableApp(val packageName: String, val label: String)

object AppSelection {
    fun protectedPackages(context: Context): Set<String> = buildSet {
        add(context.packageName)
        add("android")
        add("com.android.systemui")
        add("com.android.settings")
        add("com.android.phone")
        add("com.android.emergency")
        add("com.google.android.apps.safetyhub")
        add("com.android.packageinstaller")
        add("com.google.android.packageinstaller")
        add("com.android.permissioncontroller")
        add("com.google.android.permissioncontroller")
        android.provider.Telephony.Sms.getDefaultSmsPackage(context)?.let(::add)
        val pm = context.packageManager
        listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            Intent(Intent.ACTION_DIAL),
            Intent(Settings.ACTION_SETTINGS),
        ).forEach { intent -> pm.queryIntentActivities(intent, 0).forEach { add(it.activityInfo.packageName) } }
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(::add)
        context.getSystemService(InputMethodManager::class.java)?.inputMethodList?.forEach { add(it.packageName) }
    }

    fun load(context: Context): List<SelectableApp> {
        val pm = context.packageManager
        val protected = protectedPackages(context)
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter {
                val app = it.activityInfo.applicationInfo
                val system = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val entertainment = app.category in setOf(ApplicationInfo.CATEGORY_GAME,
                    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_SOCIAL,
                    ApplicationInfo.CATEGORY_NEWS)
                // Unclassified system utilities remain protected; known browsers/video apps may be selected.
                val reviewed = app.packageName in setOf("com.android.chrome", "com.google.android.youtube",
                    "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.microsoft.emmx")
                (!system || entertainment || reviewed) && app.uid >= 10_000 && app.packageName !in protected
            }
            .map { SelectableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun preferences(context: Context) = context.getSharedPreferences("focus", Context.MODE_PRIVATE)
    fun selected(context: Context): Set<String> = preferences(context).getStringSet("selected", emptySet())!!.toSet()
}
