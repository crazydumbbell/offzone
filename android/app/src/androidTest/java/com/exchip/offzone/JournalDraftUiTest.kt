package com.exchip.offzone

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class JournalDraftUiTest {
    @Test fun leavingRequiresExplicitDiscardAndNeverSavesDraft() {
        assumeTrue("Isolated emulator only", Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val journal = File(context.noBackupFilesDir, "journal.json")
        val original = journal.takeIf { it.exists() }?.readBytes()
        val exited = AtomicBoolean(false)
        fun text(id: Int) = context.getString(id)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { MaterialTheme { JournalScreen(true, {}, { exited.set(true) }) } }
            }
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.journal_title))), 5_000))
            repeat(5) {
                if (!device.hasObject(By.clazz("android.widget.EditText"))) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 3, 40)
                    device.waitForIdle()
                }
            }
            val input = device.findObject(By.clazz("android.widget.EditText"))
            assertNotNull("Plan editor is reachable", input)
            input.text = "A draft that must not be saved"
            device.pressBack() // Hide the keyboard if it opened.
            if (!device.hasObject(By.text(text(R.string.audit_journal_unsaved)))) device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.audit_journal_unsaved))), 3_000))
            assertFalse(exited.get())
            device.findObject(By.text(text(R.string.audit_journal_keep_editing))).click()
            assertTrue(device.wait(Until.hasObject(By.text("A draft that must not be saved")), 3_000))
            // Changing the week must not silently replace the dirty plan.
            scrollTo(device, By.desc(text(R.string.journal_next)), up = true)
            device.findObject(By.desc(text(R.string.journal_next))).click()
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.audit_journal_unsaved))), 3_000))
            device.findObject(By.text(text(R.string.audit_journal_keep_editing))).click()
            scrollTo(device, By.text("A draft that must not be saved"))

            // Confirm a different date through the native picker; keep preserves the draft.
            scrollTo(device, By.textStartsWith(text(R.string.journal_date) + ":"))
            device.findObject(By.textStartsWith(text(R.string.journal_date) + ":")).click()
            assertTrue(device.wait(Until.hasObject(By.res("android", "prev")), 3_000))
            device.findObject(By.res("android", "prev")).click()
            device.findObject(By.text("15")).click()
            device.findObject(By.res("android", "button1")).click()
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.audit_journal_unsaved))), 3_000))
            device.findObject(By.text(text(R.string.audit_journal_keep_editing))).click()
            scrollTo(device, By.text("A draft that must not be saved"), up = true)
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.audit_journal_discard))), 3_000))
            device.findObject(By.text(text(R.string.audit_journal_discard))).click()
            instrumentation.waitForIdleSync()
            assertTrue(exited.get())
            if (original == null) assertFalse(journal.exists()) else assertArrayEquals(original, journal.readBytes())
            scenario.onActivity { activity ->
                activity.setContent { MaterialTheme { JournalScreen(false, {}, {}) } }
            }
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.audit_journal_read_only))), 3_000))
            repeat(4) {
                assertFalse("Read-only journal must not show disabled editors", device.hasObject(By.clazz("android.widget.EditText")))
                device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 3, 40)
                device.waitForIdle()
            }
        }
    }

    @Test fun recreationRestoresBothDraftsBeforeAsynchronousStoreLoad() {
        assumeTrue("Isolated emulator with post-create lifecycle callbacks", Build.VERSION.SDK_INT >= 29 &&
            (Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val application = context.applicationContext as Application
        val journal = File(context.noBackupFilesDir, "journal.json")
        val original = journal.takeIf { it.exists() }?.readBytes()
        // Replace test content before the first frame on BOTH instances. Replacing it after
        // recreation would let MainActivity consume the saved Compose registry first.
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPostCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity) activity.setContent {
                    MaterialTheme { JournalScreen(true, {}, {}) }
                }
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.journal_title))), 5_000))
                scrollTo(device, By.clazz("android.widget.EditText"))
                device.findObject(By.clazz("android.widget.EditText")).text = "Plan survives recreation"
                scenario.onActivity { activity ->
                    activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                }
                scrollTo(device, By.text(context.getString(R.string.journal_note)))
                device.findObjects(By.clazz("android.widget.EditText")).last().text = "Reflection survives recreation"
                scenario.recreate()
                scenario.onActivity { activity ->
                    activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                }
                instrumentation.waitForIdleSync()
                scrollTo(device, By.text("Plan survives recreation"), up = true)
                scrollTo(device, By.text("Reflection survives recreation"))
                device.pressBack()
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.audit_journal_unsaved))), 3_000))
                if (original == null) assertFalse(journal.exists()) else assertArrayEquals(original, journal.readBytes())
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    private fun scrollTo(device: UiDevice, selector: BySelector, up: Boolean = false) {
        repeat(9) {
            if (device.wait(Until.hasObject(selector), 300)) return
            val low = device.displayHeight * 4 / 5
            val high = device.displayHeight / 3
            device.swipe(device.displayWidth / 2, if (up) high else low,
                device.displayWidth / 2, if (up) low else high, 40)
            device.waitForIdle()
        }
        fail("Missing journal UI: $selector")
    }

}
