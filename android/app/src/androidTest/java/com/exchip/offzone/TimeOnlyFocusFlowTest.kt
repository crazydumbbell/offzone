package com.exchip.offzone

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
class TimeOnlyFocusFlowTest {
    @Test fun timeOnlyRuleNeedsNoLocationAndStartsOnlyOnIntentInsideItsSchedule() {
        assumeTrue("Accessibility settings are emulator-only", Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val device = UiDevice.getInstance(instrumentation)
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
        val target = instrumentation.context.packageName
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        val inside = FocusRule(name = "Time-only runtime test", packages = setOf(target), startMinutes = (minute + 1439) % 1440,
            endMinutes = (minute + 60) % 1440, latitude = 0.0, longitude = 0.0, anywhere = true)
        val outside = inside.copy(id = java.util.UUID.randomUUID().toString(), startMinutes = (minute + 120) % 1440, endMinutes = (minute + 180) % 1440)
        fun waitFor(timeout: Long = 10_000, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + timeout
            while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
            assertTrue("Timed out waiting for engine state: ${FocusController.state.value}", condition())
        }
        fun openApp() {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.home_your_space))), 10_000))
        }
        try {
            preferences.edit().putBoolean("disclosure", true).commit()
            onboarding.edit().putBoolean("completed", true).commit()
            device.executeShellCommand("settings put secure enabled_accessibility_services ${(services.split(':').filter { it.isNotBlank() } + service).distinct().joinToString(":")}")
            device.executeShellCommand("settings put secure accessibility_enabled 1")
            waitFor { FocusController.state.value.connected }
            // Saved with the editor's own shape: the rule round-trips through storage with its flag.
            instrumentation.runOnMainSync {
                assertTrue(FocusController.ruleStore.save(inside))
                assertTrue(FocusController.ruleStore.save(outside))
                assertTrue(FocusController.ruleStore.rules.value.first { it.id == inside.id }.anywhere)
                assertTrue(FocusController.activateRule(outside.id))
            }
            openApp()
            instrumentation.runOnMainSync {
                FocusController.startPlaceFocus(context)
                assertNull("Outside its schedule nothing starts", FocusController.state.value.session)
                assertEquals(R.string.engine_outside_schedule, FocusController.state.value.message)
                assertTrue(FocusController.activateRule(inside.id))
                assertNull("Activation alone never blocks", FocusController.state.value.session)
                FocusController.checkArrival(context)
                FocusController.startPlaceMonitoring(context)
                assertNull("Arrival checks do not apply to a time-only rule", FocusController.state.value.session)
                assertFalse(FocusController.state.value.monitoringPlace)
                assertFalse(FocusController.state.value.checkingPlace)
                FocusController.startPlaceFocus(context)
            }
            waitFor { FocusController.state.value.session != null && FocusController.state.value.timeOnlySession }
            assertFalse(FocusController.state.value.placeSession)
            context.startActivity(Intent().setComponent(ComponentName(target, FocusTestActivity::class.java.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.blocked_title))), 5_000))
            // No location samples exist; the session must not fail open the way a place session does.
            SystemClock.sleep(35_000)
            assertNotNull("A time-only session does not depend on location fixes", FocusController.state.value.session)
            instrumentation.runOnMainSync { FocusController.stop() }
            waitFor { FocusController.state.value.session == null }
            assertFalse(FocusController.state.value.timeOnlySession)
            assertTrue(device.wait(Until.gone(By.text(context.getString(R.string.blocked_title))), 5_000))
        } finally {
            instrumentation.runOnMainSync { FocusController.pauseRule() }
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
