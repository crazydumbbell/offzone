package com.exchip.offzone

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationDisclosureTest {
    @Test fun monitoringRequiresChoiceAndRejectsStaleConsent() {
        assumeTrue("Isolated emulator only", Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        // Runner setup pregrants these on the isolated emulator; keep OS permission dialogs out of this consent test.
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        if (Build.VERSION.SDK_INT >= 33) assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        instrumentation.runOnMainSync { FocusController.initialize(context) }
        assumeTrue(FocusController.state.value.session == null && !FocusController.state.value.monitoringPlace)
        val originalRule = FocusController.state.value.appliedRule
        val originalNotifications = FocusController.notificationsEnabled.value
        val rule = FocusRule("location-disclosure-test", "Location consent test", setOf("com.android.settings"), 0, 1439, 37.5665, 126.978)
        fun clearCache() { if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache() }
        fun click(id: Int) {
            val text = context.getString(id)
            repeat(10) {
                clearCache()
                val found = device.findObject(By.text(text))
                if (found != null) {
                    device.waitForIdle(1_000); SystemClock.sleep(500); clearCache()
                    device.findObject(By.text(text))?.click()
                    device.waitForIdle(1_000); SystemClock.sleep(500); clearCache(); return
                }
                device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 3, 50)
                device.waitForIdle(1_000); SystemClock.sleep(200)
            }
            device.takeScreenshot(File(context.getExternalFilesDir(null), "location-disclosure-failure.png"))
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "location-disclosure-failure.xml"))
            fail("Missing $text")
        }
        fun assertIdle(generation: Long) {
            assertEquals(generation, FocusController.generation)
            assertFalse(FocusController.state.value.checkingPlace)
            assertFalse(FocusController.state.value.monitoringPlace)
            assertNull(FocusController.state.value.session)
        }
        try {
            instrumentation.runOnMainSync {
                assertTrue(FocusController.ruleStore.save(rule))
                assertTrue(FocusController.activateRule(rule.id))
            }
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            click(R.string.engine_monitor_start)
            clearCache()
            assertTrue(device.hasObject(By.text(context.getString(R.string.location_disclosure_title))))
            val before = FocusController.generation
            assertIdle(before)
            click(R.string.not_now)
            assertIdle(before)
            clearCache()
            assertFalse(device.hasObject(By.text(context.getString(R.string.location_disclosure_title))))

            click(R.string.engine_monitor_start)
            assertTrue(device.hasObject(By.text(context.getString(R.string.location_disclosure_title))))
            instrumentation.runOnMainSync { FocusController.stop() }
            val invalidated = FocusController.generation
            click(R.string.location_disclosure_agree)
            assertIdle(invalidated)

            click(R.string.engine_monitor_start)
            assertTrue(device.hasObject(By.text(context.getString(R.string.location_disclosure_title))))
            val accepted = FocusController.generation
            assertIdle(accepted)
            click(R.string.location_disclosure_agree)
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (FocusController.generation == accepted && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            assertTrue("Agree dispatches the new monitoring request", FocusController.generation > accepted)
        } finally {
            instrumentation.runOnMainSync {
                FocusController.stop()
                FocusController.ruleStore.delete(rule.id)
                if (originalRule != null) FocusController.activateRule(originalRule.id) else FocusController.pauseRule()
                FocusController.setNotificationsEnabled(originalNotifications)
            }
            device.pressHome()
        }
    }
}
