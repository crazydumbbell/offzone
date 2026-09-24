package com.exchip.offzone

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class PlaceFocusFlowTest {
    @Suppress("DEPRECATION")
    @Test fun foregroundLocationStartsOnlyOnIntentAndExitOrStaleFixRestoresAccess() {
        assumeTrue("Mock location and accessibility settings are emulator-only", Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Root test runner grants precise location before executing this test.
        assumeTrue("Grant precise location to the debug app before this test", PlaceMonitor.permissionReady(context))
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val device = UiDevice.getInstance(instrumentation)
        val manager = context.getSystemService(LocationManager::class.java)
        val service = ComponentName(context, FocusAccessibilityService::class.java).flattenToString()
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val accessibility = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val preferences = AppSelection.preferences(context)
        val disclosure = preferences.getBoolean("disclosure", false)
        val onboarding = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        val onboardingCompleted = onboarding.all["completed"] as? Boolean
        val rulesPreferences = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        val savedRules = rulesPreferences.getString("saved", null)
        val appliedRule = rulesPreferences.getString("applied", null)
        val originalRules = rulesPreferences.getString("original", null)
        val mockSetting = device.executeShellCommand("appops get ${context.packageName} android:mock_location")
        val mockMode = Regex("mock_location: (allow|ignore|deny|default|foreground)").find(mockSetting)?.groupValues?.get(1) ?: "default"
        val target = instrumentation.context.packageName
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        val rule = FocusRule(name = "Place runtime test", packages = setOf(target), startMinutes = (minute + 1439) % 1440,
            endMinutes = (minute + 60) % 1440, latitude = 37.0, longitude = 127.0)
        fun waitFor(timeout: Long = 10_000, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + timeout
            while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
            assertTrue("Timed out waiting for engine state: ${FocusController.state.value}", condition())
        }
        fun fix(latitude: Double = 37.0) {
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                this.latitude = latitude; longitude = 127.0; accuracy = 5f
                time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            })
        }
        fun openApp() {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue(device.wait(Until.hasObject(By.text("offzone")), 10_000))
        }
        fun startPlace() {
            openApp()
            instrumentation.runOnMainSync { FocusController.startPlaceFocus(context) }
            repeat(20) { if (FocusController.state.value.session == null) { fix(); SystemClock.sleep(300) } }
            waitFor { FocusController.state.value.placeSession }
        }
        try {
            preferences.edit().putBoolean("disclosure", true).commit()
            onboarding.edit().putBoolean("completed", true).commit()
            device.executeShellCommand("appops set ${context.packageName} android:mock_location allow")
            manager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            manager.addTestProvider(LocationManager.NETWORK_PROVIDER, false, false, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE)
            manager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
            device.executeShellCommand("settings put secure enabled_accessibility_services ${(services.split(':').filter { it.isNotBlank() } + service).distinct().joinToString(":")}")
            device.executeShellCommand("settings put secure accessibility_enabled 1")
            waitFor { FocusController.state.value.connected }
            instrumentation.runOnMainSync {
                assertTrue(FocusController.ruleStore.save(rule))
                assertTrue(FocusController.activateRule(rule.id))
            }
            openApp()
            instrumentation.runOnMainSync { FocusController.startPlaceMonitoring(context) }
            repeat(30) { if (!FocusController.state.value.insidePlace) { fix(); SystemClock.sleep(300) } }
            waitFor { FocusController.state.value.monitoringPlace && FocusController.state.value.insidePlace }
            assertNull("Arrival monitoring must never start blocking", FocusController.state.value.session)
            startPlace()
            context.startActivity(Intent().setComponent(ComponentName(target, FocusTestActivity::class.java.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.blocked_title))), 5_000))
            repeat(30) { if (FocusController.state.value.session != null) { fix(37.01); SystemClock.sleep(300) } }
            waitFor { FocusController.state.value.session == null }
            assertTrue(device.wait(Until.gone(By.text(context.getString(R.string.blocked_title))), 5_000))
            assertTrue("Explicit monitoring survives an ordinary exit", FocusController.state.value.monitoringPlace)
            repeat(30) { if (!FocusController.state.value.insidePlace) { fix(); SystemClock.sleep(300) } }
            waitFor { FocusController.state.value.insidePlace }
            assertNull("Re-entry must not restart blocking", FocusController.state.value.session)
            startPlace()
            // No further samples: the active foreground monitor must fail open itself.
            waitFor(35_000) { FocusController.state.value.session == null }
            assertEquals(R.string.engine_location_lost, FocusController.state.value.message)
            assertFalse("Lost location stops monitoring too", FocusController.state.value.monitoringPlace)
            openApp()
            instrumentation.runOnMainSync { FocusController.startPlaceMonitoring(context) }
            repeat(30) { if (!FocusController.state.value.insidePlace) { fix(); SystemClock.sleep(300) } }
            waitFor { FocusController.state.value.monitoringPlace && FocusController.state.value.insidePlace }
            instrumentation.runOnMainSync { FocusController.stopPlaceMonitoring() }
            assertFalse(FocusController.state.value.monitoringPlace)
            assertNull(FocusController.state.value.session)
        } finally {
            instrumentation.runOnMainSync { FocusController.pauseRule() }
            runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
            runCatching { manager.removeTestProvider(LocationManager.NETWORK_PROVIDER) }
            device.executeShellCommand("appops set ${context.packageName} android:mock_location $mockMode")
            device.executeShellCommand(if (services.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $services")
            device.executeShellCommand("settings put secure accessibility_enabled $accessibility")
            preferences.edit().putBoolean("disclosure", disclosure).commit()
            onboarding.edit().apply { if (onboardingCompleted == null) remove("completed") else putBoolean("completed", onboardingCompleted) }.commit()
            rulesPreferences.edit().apply {
                if (savedRules == null) remove("saved") else putString("saved", savedRules)
                if (appliedRule == null) remove("applied") else putString("applied", appliedRule)
                if (originalRules == null) remove("original") else putString("original", originalRules)
            }.commit()
        }
    }
}
