package com.exchip.offzone

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusFlowTest {
    @Test fun blockRestoreExpireAndRevokeOnIsolatedEmulator() {
        assumeTrue("This test changes accessibility settings only on an emulator", Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val device = UiDevice.getInstance(instrumentation)
        val service = ComponentName(context, FocusAccessibilityService::class.java).flattenToString()
        val previous = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val wasEnabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val preferences = AppSelection.preferences(context)
        val onboarding = context.getSharedPreferences("onboarding", android.content.Context.MODE_PRIVATE)
        val onboardingCompleted = onboarding.all["completed"] as? Boolean
        val previousSelection = AppSelection.selected(context)
        val previousDisclosure = preferences.getBoolean("disclosure", false)
        val target = instrumentation.context.packageName
        fun waitFor(test: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 10_000
            while (!test() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
            assertTrue(test())
        }
        fun scrollTo(label: String) {
            repeat(5) {
                if (device.wait(Until.hasObject(By.text(label)), 1_000)) return
                device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                    device.displayWidth / 2, device.displayHeight / 3, 30)
            }
            assertTrue("Missing UI action: $label", device.hasObject(By.text(label)))
        }
        fun launchTarget() {
            context.startActivity(Intent().setComponent(ComponentName(target, FocusTestActivity::class.java.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        fun start() {
            instrumentation.runOnMainSync { assertTrue(FocusController.start(setOf(target), 1)) }
            launchTarget()
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.blocked_title))), 5_000))
        }
        try {
            preferences.edit().putBoolean("disclosure", true).commit()
            onboarding.edit().putBoolean("completed", true).commit()
            device.executeShellCommand("settings put secure enabled_accessibility_services ${(previous.split(':').filter { it.isNotBlank() } + service).distinct().joinToString(":")}")
            device.executeShellCommand("settings put secure accessibility_enabled 1")
            waitFor { FocusController.state.value.connected }
            assertTrue(AppSelection.load(context).any { it.packageName == target })
            assertFalse(AppSelection.load(context).any { it.packageName == context.packageName || it.packageName == "com.android.settings" })
            instrumentation.runOnMainSync {
                val oldGeneration = FocusController.generation
                FocusController.stop()
                assertFalse(FocusController.start(setOf(target), 1, oldGeneration))
            }
            // Exercise actual picker and Start focus once, then inspect the native shield.
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            scrollTo(context.getString(R.string.m_quick))
            device.findObject(By.text(context.getString(R.string.m_quick))).click()
            scrollTo(context.getString(R.string.choose_apps))
            device.findObject(By.text(context.getString(R.string.choose_apps))).click()
            assertTrue(device.wait(Until.hasObject(By.text("Focus test app")), 5_000))
            device.findObject(By.text("Focus test app")).click()
            device.findObject(By.text(context.getString(R.string.done))).click()
            scrollTo(context.getString(R.string.start_focus))
            device.findObject(By.text(context.getString(R.string.start_focus))).click()
            waitFor { FocusController.state.value.session != null }
            launchTarget()
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.blocked_title))), 5_000))
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "shield.png"))
            device.findObject(By.text(context.getString(R.string.restore))).click()
            waitFor { FocusController.state.value.session == null }
            assertTrue(device.wait(Until.gone(By.text(context.getString(R.string.blocked_title))), 5_000))
            start()
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue(device.wait(Until.gone(By.text(context.getString(R.string.blocked_title))), 5_000))
            launchTarget()
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.blocked_title))), 5_000))
            // Real monotonic expiry while the test app remains foreground.
            assertTrue(device.wait(Until.gone(By.text(context.getString(R.string.blocked_title))), 65_000))
            waitFor { FocusController.state.value.session == null }
            start()
            device.executeShellCommand(if (previous.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $previous")
            waitFor { !FocusController.state.value.connected }
            assertNull(FocusController.state.value.session)
            assertTrue(device.wait(Until.gone(By.text(context.getString(R.string.blocked_title))), 5_000))
        } finally {
            instrumentation.runOnMainSync { FocusController.stop() }
            preferences.edit().putBoolean("disclosure", previousDisclosure).putStringSet("selected", previousSelection).commit()
            onboarding.edit().apply { if (onboardingCompleted == null) remove("completed") else putBoolean("completed", onboardingCompleted) }.commit()
            device.executeShellCommand(if (previous.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $previous")
            device.executeShellCommand("settings put secure accessibility_enabled $wasEnabled")
        }
    }
}
