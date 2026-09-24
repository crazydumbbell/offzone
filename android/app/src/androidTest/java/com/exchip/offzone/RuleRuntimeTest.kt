package com.exchip.offzone

import android.location.Location
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleRuntimeTest {
    @Test fun savedEditsDoNotApplyAndRecoveryRejectsLateLocation() {
        assumeTrue(Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("rules", android.content.Context.MODE_PRIVATE)
        val saved = preferences.getString("saved", null)
        val applied = preferences.getString("applied", null)
        val backup = preferences.getString("original", null)
        instrumentation.runOnMainSync {
            FocusController.initialize(context)
            val store = FocusController.ruleStore
            val rule = FocusRule(name = "Runtime check", packages = setOf(instrumentation.context.packageName),
                startMinutes = 0, endMinutes = 1439, latitude = 37.0, longitude = 127.0)
            try {
                assertTrue(store.save(rule))
                assertTrue(FocusController.activateRule(rule.id))
                assertTrue(store.save(rule.copy(name = "Edited but not applied")))
                assertEquals("Runtime check", FocusController.state.value.appliedRule?.name)
                assertTrue(store.hasUnappliedChanges(rule.id))
                assertTrue(FocusController.activateRule(rule.id))
                assertFalse(store.hasUnappliedChanges(rule.id))
                val oldGeneration = FocusController.generation
                FocusController.stop()
                val location = Location("test").apply {
                    latitude = 37.0; longitude = 127.0; accuracy = 5f
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                FocusController.acceptLocation(location, oldGeneration, true)
                assertNull(FocusController.state.value.session)
                assertFalse(FocusController.state.value.insidePlace)
                assertTrue(store.delete(rule.id))
                assertNull(FocusController.state.value.appliedRule)
                assertFalse(FocusController.state.value.ruleEnabled)
                preferences.edit().putString("saved", "broken-original").commit()
                assertTrue(store.error.value)
                assertFalse(store.save(rule))
                assertEquals("broken-original", preferences.getString("saved", null))
            } finally {
                FocusController.pauseRule()
                preferences.edit().apply {
                    if (saved == null) remove("saved") else putString("saved", saved)
                    if (applied == null) remove("applied") else putString("applied", applied)
                    if (backup == null) remove("original") else putString("original", backup)
                }.commit()
            }
        }
    }
}
