package com.exchip.offzone

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class AuditHomeUiTest {
    @Test fun onboardingCancelLockedGateAndProHomeFlow() {
        assumeTrue("Isolated emulator only", Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        instrumentation.runOnMainSync { FocusController.initialize(context) }
        assumeTrue("Do not interrupt existing focus or location checks", FocusController.state.value.let {
            it.session == null && !it.monitoringPlace && !it.checkingPlace
        })
        assumeTrue("Preserve unreadable user data", !FocusController.ruleStore.error.value)
        val originalState = FocusController.state.value
        val onboarding = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        val rules = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        val originalOnboarding = onboarding.all.toMap()
        val originalRules = rules.all.toMap()
        val originalFontScale = Settings.System.getString(context.contentResolver, "font_scale")?.toFloatOrNull()
        var changedFontScale = false
        fun visible(selector: BySelector): Boolean {
            if (Build.VERSION.SDK_INT >= 33) instrumentation.getUiAutomation(androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags).clearCache()
            return device.hasObject(selector)
        }
        fun visible(id: Int) = visible(By.text(context.getString(id)))
        fun awaitSelector(selector: BySelector, label: String, timeout: Long = 10_000) {
            val deadline = SystemClock.elapsedRealtime() + timeout
            while (SystemClock.elapsedRealtime() < deadline) {
                if (visible(selector)) return
                SystemClock.sleep(200)
            }
            assertTrue("Missing $label", visible(selector))
        }
        fun awaitText(id: Int, timeout: Long = 10_000) = awaitSelector(By.text(context.getString(id)), context.getString(id), timeout)
        fun clickSelector(selector: BySelector, label: String) {
            repeat(5) {
                if (!visible(selector)) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                        device.displayWidth / 2, device.displayHeight / 3, 40)
                    SystemClock.sleep(200)
                }
            }
            awaitSelector(selector, label)
            val target = device.findObject(selector) ?: device.wait(androidx.test.uiautomator.Until.findObject(selector), 5_000)
            assertNotNull("Missing $label", target)
            target!!.click()
        }
        fun click(id: Int) = clickSelector(By.text(context.getString(id)), context.getString(id))
        fun openApp(debugPro: Boolean? = null) {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (debugPro != null) intent.putExtra(ProAccessGate.EXTRA_DEBUG_PRO, debugPro)
            context.startActivity(intent)
            device.waitForIdle(2_000)
            SystemClock.sleep(500)
        }
        fun screenshot(name: String) {
            SystemClock.sleep(400)
            assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "audit-$name.png")))
        }
        try {
            instrumentation.runOnMainSync {
                FocusController.pauseRule()
                assertTrue(rules.edit().clear().commit())
                assertTrue(onboarding.edit().clear().commit())
            }
            openApp(debugPro = false)
            awaitText(R.string.onboarding_title)
            assertTrue(visible(R.string.onboarding_sign_in))
            assertFalse(visible(R.string.restore))
            click(R.string.onboarding_start)
            awaitText(R.string.editor_apps_title)
            click(R.string.editor_cancel)
            awaitText(R.string.onboarding_title)
            assertFalse(OnboardingProfile.completed(context))

            // Completed onboarding without Pro lands on Locked Home.
            instrumentation.runOnMainSync { assertTrue(OnboardingProfile.complete(context)) }
            openApp(debugPro = false)
            awaitText(R.string.home_your_space)
            awaitText(R.string.home_unlock_pro)
            assertFalse("Idle Home must never show Restore access", visible(R.string.restore))
            click(R.string.m_quick)
            awaitSelector(By.desc(context.getString(R.string.pro_close)), context.getString(R.string.pro_close))
            clickSelector(By.desc(context.getString(R.string.pro_close)), context.getString(R.string.pro_close))
            awaitText(R.string.home_unlock_pro)

            // Pro Home flow + Settings + Schedule transition + Large font check.
            openApp(debugPro = true)
            val unlockDeadline = SystemClock.elapsedRealtime() + 8_000
            while (visible(R.string.home_unlock_pro) && SystemClock.elapsedRealtime() < unlockDeadline) SystemClock.sleep(200)
            assertFalse(visible(R.string.home_unlock_pro))
            awaitText(R.string.home_your_space)
            clickSelector(By.desc(context.getString(R.string.audit_settings)), context.getString(R.string.audit_settings))
            awaitText(R.string.m_account)
            assertTrue(visible(R.string.m_notifications))
            assertFalse("Idle Settings must never show Restore access", visible(R.string.restore))
            screenshot("settings")
            device.pressBack()
            awaitText(R.string.home_your_space)
            assertFalse(visible(R.string.m_account))
            assertFalse(visible(R.string.engine_monitor_start))
            assertFalse(visible(R.string.m_check_arrival))

            // No OS clock mutation: leave enough launch time before the next natural minute boundary.
            if (ZonedDateTime.now().second > 45) {
                val minute = ZonedDateTime.now().minute
                while (ZonedDateTime.now().minute == minute) SystemClock.sleep(200)
            }
            val now = ZonedDateTime.now()
            val start = (now.hour * 60 + now.minute + 1) % 1440
            val rule = FocusRule(name = "Idle schedule regression", packages = setOf(instrumentation.context.packageName),
                startMinutes = start, endMinutes = (start + 60) % 1440,
                latitude = 37.0, longitude = 127.0)
            instrumentation.runOnMainSync {
                // Fake only engine readiness. Never enable Accessibility or accept Location consent.
                FocusController.connect()
                assertTrue(FocusController.ruleStore.save(rule))
                assertTrue(FocusController.activateRule(rule.id))
            }
            awaitText(R.string.home_scheduled)
            screenshot("home-upcoming")
            assertFalse(visible(R.string.start_focus))
            assertNull(FocusController.state.value.observedAt)
            val generation = FocusController.generation
            // Only the UI clock may cause this transition; no engine mutation, navigation or GPS fix.
            awaitText(R.string.start_focus, 65_000)
            assertEquals(generation, FocusController.generation)
            assertNull(FocusController.state.value.observedAt)
            assertFalse(visible(R.string.home_scheduled))
            screenshot("home-ready")
            click(R.string.start_focus)
            awaitText(R.string.location_disclosure_title)
            assertEquals(generation, FocusController.generation)
            assertFalse(FocusController.state.value.checkingPlace)
            click(R.string.not_now)
            assertEquals(generation, FocusController.generation)
            assertNull(FocusController.state.value.session)
            // Quick focus remains directly accessible alongside a configured place rule.
            click(R.string.m_quick)
            awaitText(R.string.m_quick_note)
            // Exercise actual system text scaling and Activity recreation, without a UI test override.
            changedFontScale = true
            device.executeShellCommand("settings put system font_scale 2.0")
            val fontDeadline = SystemClock.elapsedRealtime() + 10_000
            while (context.resources.configuration.fontScale < 1.99f && SystemClock.elapsedRealtime() < fontDeadline) {
                SystemClock.sleep(200)
            }
            assertEquals(2f, context.resources.configuration.fontScale, 0.01f)
            awaitText(R.string.m_quick)
            screenshot("quick-large-font")
            device.pressBack()
            awaitText(R.string.home_your_space)
            screenshot("home-large-font")
            clickSelector(By.desc(context.getString(R.string.audit_settings)), context.getString(R.string.audit_settings))
            awaitText(R.string.m_account)
            screenshot("settings-large-font")
        } finally {
            ProAccessGate.debugProOverride = null
            try { instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .toList().forEach { it.finish() }
                FocusController.pauseRule()
                restore(onboarding, originalOnboarding)
                restore(rules, originalRules)
                originalState.appliedRule?.let {
                    // Restore the exact applied snapshot, including saved-but-unapplied edits.
                    assertTrue(FocusController.ruleStore.save(it))
                    assertTrue(FocusController.activateRule(it.id))
                    restore(rules, originalRules)
                }
                if (!originalState.connected) FocusController.disconnect()
            } } finally {
                if (changedFontScale) device.executeShellCommand(if (originalFontScale == null)
                    "settings delete system font_scale" else "settings put system font_scale $originalFontScale")
            }
        }
    }

    private fun restore(preferences: SharedPreferences, values: Map<String, *>) {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        assertTrue(editor.commit())
    }
}
