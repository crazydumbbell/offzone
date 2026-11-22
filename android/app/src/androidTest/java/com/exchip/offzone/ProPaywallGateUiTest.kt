package com.exchip.offzone

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
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

@RunWith(AndroidJUnit4::class)
class ProPaywallGateUiTest {
    @Test fun lockedHomeGatesActionsWhileActiveSessionRestoreRemainsFree() {
        assumeTrue("Isolated emulator only", Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        instrumentation.runOnMainSync { FocusController.initialize(context) }
        assumeTrue("Do not interrupt active focus", FocusController.state.value.session == null)
        assumeTrue("Preserve unreadable user data", !FocusController.ruleStore.error.value)
        val originalConnected = FocusController.state.value.connected
        val onboarding = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        val rules = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        val originalOnboarding = onboarding.all.toMap()
        val originalRules = rules.all.toMap()
        fun visible(selector: BySelector): Boolean {
            if (Build.VERSION.SDK_INT >= 33) {
                instrumentation.getUiAutomation(androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags).clearCache()
            }
            return device.hasObject(selector)
        }
        fun awaitSelector(selector: BySelector, label: String, timeout: Long = 8_000) {
            val deadline = SystemClock.elapsedRealtime() + timeout
            while (SystemClock.elapsedRealtime() < deadline) {
                if (visible(selector)) return
                SystemClock.sleep(150)
            }
            assertTrue("Missing $label", visible(selector))
        }
        fun click(selector: BySelector, label: String) {
            repeat(5) {
                if (!visible(selector)) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                        device.displayWidth / 2, device.displayHeight / 3, 40)
                    device.waitForIdle(1_000)
                    SystemClock.sleep(300)
                }
            }
            awaitSelector(selector, label)
            val target = device.findObject(selector) ?: device.wait(androidx.test.uiautomator.Until.findObject(selector), 5_000)
            assertNotNull("Missing $label", target)
            target!!.click()
            device.waitForIdle(1_000)
        }
        try {
            val sampleRule = FocusRule(
                name = "Locked test rule",
                packages = setOf(instrumentation.context.packageName),
                startMinutes = 9 * 60,
                endMinutes = 17 * 60,
                latitude = 0.0,
                longitude = 0.0,
                anywhere = true,
            )
            instrumentation.runOnMainSync {
                FocusController.pauseRule()
                assertTrue(rules.edit().clear().commit())
                assertTrue(OnboardingProfile.complete(context))
                assertTrue(FocusController.ruleStore.save(sampleRule))
            }
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(ProAccessGate.EXTRA_DEBUG_PRO, false)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            awaitSelector(By.text(context.getString(R.string.home_locked_title)), "locked title")
            awaitSelector(By.text(context.getString(R.string.home_unlock_pro)), "unlock cta")
            assertFalse("Idle locked home must never show Restore access", visible(By.text(context.getString(R.string.restore))))

            // Tapping Apply on a saved rule without Pro opens the Paywall instead of activating.
            click(By.text(context.getString(R.string.m_apply)), "apply")
            awaitSelector(By.desc(context.getString(R.string.pro_close)), "paywall close")
            assertNull(FocusController.state.value.appliedRule)
            click(By.desc(context.getString(R.string.pro_close)), "paywall close")
            awaitSelector(By.text(context.getString(R.string.home_unlock_pro)), "unlock cta")

            // Safety Invariant: an active focus session ALWAYS shows Restore access even without Pro.
            instrumentation.runOnMainSync {
                FocusController.connect()
                assertTrue(FocusController.start(setOf(instrumentation.context.packageName), 15))
            }
            repeat(3) {
                if (!visible(By.text(context.getString(R.string.restore)))) {
                    device.swipe(device.displayWidth / 2, device.displayHeight / 3,
                        device.displayWidth / 2, device.displayHeight * 4 / 5, 40)
                    device.waitForIdle(1_000)
                }
            }
            awaitSelector(By.text(context.getString(R.string.home_in_focus)), "in focus")
            awaitSelector(By.text(context.getString(R.string.restore)), "restore access")
            click(By.text(context.getString(R.string.restore)), "restore access")
            awaitSelector(By.text(context.getString(R.string.home_unlock_pro)), "locked home after restore")
            assertNull(FocusController.state.value.session)
            assertFalse("Idle home must hide Restore access after stopping", visible(By.text(context.getString(R.string.restore))))
        } finally {
            ProAccessGate.debugProOverride = null
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .toList().forEach { it.finish() }
                FocusController.stop()
                FocusController.pauseRule()
                if (!originalConnected) FocusController.disconnect()
                restore(onboarding, originalOnboarding)
                restore(rules, originalRules)
            }
        }
    }

    private fun restore(prefs: SharedPreferences, values: Map<String, *>) {
        val editor = prefs.edit().clear()
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
