package com.exchip.offzone

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class WeekdayEditorUiTest {
    @Test fun oldRulesDecodeAsDailyAndEditorSavesChosenDays() {
        assumeTrue("Isolated emulator only", Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        fun text(id: Int) = context.getString(id)
        fun await(selector: androidx.test.uiautomator.BySelector, present: Boolean = true) {
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
                if (device.hasObject(selector) == present) break
                SystemClock.sleep(150)
            }
            assertEquals(selector.toString(), present, device.hasObject(selector))
        }
        fun tap(selector: androidx.test.uiautomator.BySelector) { await(selector); device.findObject(selector).click() }

        // Rules saved before weekdays existed have no "days" key and keep running every day.
        val legacy = RuleStore.encode(FocusRule(name = "Old", packages = setOf("example.app"), startMinutes = 540,
            endMinutes = 600, latitude = 37.0, longitude = 127.0)).apply { remove("days") }
        assertEquals(RulePolicy.EVERY_DAY, RuleStore.decode(JSONObject(legacy.toString())).days)
        assertThrows(IllegalArgumentException::class.java) { RuleStore.decode(JSONObject(legacy.toString()).put("days", 0)) }

        instrumentation.runOnMainSync { FocusController.initialize(context) }
        assumeTrue("Preserve unreadable user data", !FocusController.ruleStore.error.value)
        val rules = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        val original = rules.all.toMap()
        val rule = FocusRule(name = "Weekday editor test", packages = setOf("example.app"), startMinutes = 9 * 60,
            endMinutes = 17 * 60, latitude = 37.0, longitude = 127.0, placeLabel = "Desk")
        val saved = AtomicReference<FocusRule?>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent { OffzoneTheme { RuleEditorScreen(rule, rule.startMinutes, {}, { saved.set(it) }) } }
                }
                listOf(R.string.editor_next_place, R.string.editor_next_time).forEach {
                    tap(By.text(text(it)))
                }
                await(By.text(text(R.string.editor_days)))
                fun day(value: DayOfWeek) = By.desc(value.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                // Clearing every day blocks the next step and says why.
                DayOfWeek.entries.forEach { tap(day(it)) }
                await(By.text(text(R.string.editor_days_warning)))
                tap(By.text(text(R.string.editor_next_review)))
                SystemClock.sleep(400)
                assertTrue("Still on the schedule step", device.hasObject(By.text(text(R.string.editor_days_warning))))
                DayOfWeek.entries.take(5).forEach { tap(day(it)) }
                await(By.text(text(R.string.editor_days_warning)), present = false)
                SystemClock.sleep(400)
                assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "weekday-editor.png")))
                tap(By.text(text(R.string.editor_next_review)))
                await(By.textContains(text(R.string.days_weekdays)))
                assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "weekday-review.png")))
                tap(By.text(text(R.string.m_save_rule)))
                val deadline = SystemClock.elapsedRealtime() + 5_000
                while (saved.get() == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            }
            assertEquals(RulePolicy.WEEKDAYS, saved.get()?.days)
            assertEquals(RulePolicy.WEEKDAYS, FocusController.ruleStore.rules.value.single { it.id == rule.id }.days)
        } finally {
            instrumentation.runOnMainSync {
                val editor = rules.edit().clear()
                original.forEach { (key, value) -> if (value is String) editor.putString(key, value) }
                assertTrue(editor.commit())
            }
        }
    }
}
