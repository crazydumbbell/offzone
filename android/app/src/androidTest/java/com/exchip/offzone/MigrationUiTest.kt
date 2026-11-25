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
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MigrationUiTest {
    @Test fun onboardingRuleDraftApplyEditAndPersonalScreens() {
        assumeTrue("Isolated emulator only", Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        if (device.hasObject(By.textContains("System UI"))) {
            device.findObject(By.text("Close app"))?.click()
            device.waitForIdle(1_000)
        }
        val onboarding = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        val rules = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        val originalOnboarding = onboarding.all.toMap(); val originalRules = rules.all.toMap()
        instrumentation.runOnMainSync { FocusController.initialize(context) }
        assumeTrue("Do not interrupt existing focus", FocusController.state.value.session == null)
        val originalApplied = FocusController.state.value.appliedRule
        fun label(id: Int) = context.getString(id)
        fun waitFor(description: String, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            assertTrue(description, condition())
        }
        fun scrollToSelector(selector: BySelector, description: String) {
            repeat(9) {
                if (Build.VERSION.SDK_INT >= 33) instrumentation.getUiAutomation(androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags).clearCache()
                if (device.wait(Until.hasObject(selector), 700)) return
                device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 3, 60)
                device.waitForIdle(1_000); SystemClock.sleep(300)
            }
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "migration-failure.xml"))
            device.takeScreenshot(File(context.getExternalFilesDir(null), "migration-failure.png"))
            fail("Missing UI: $description")
        }
        fun scrollTo(text: String) = scrollToSelector(By.text(text), text)
        fun clickSelector(selector: BySelector, description: String) {
            scrollToSelector(selector, description)
            device.waitForIdle(1_000); SystemClock.sleep(300)
            if (Build.VERSION.SDK_INT >= 33) instrumentation.getUiAutomation(androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags).clearCache()
            val target = device.wait(Until.findObject(selector), 5_000)
            assertNotNull("Missing $description", target)
            target!!.click(); device.waitForIdle(1_000)
            if (Build.VERSION.SDK_INT >= 33) instrumentation.getUiAutomation(androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags).clearCache()
        }
        fun click(text: String) = clickSelector(By.text(text), text)
        fun top() {
            repeat(5) { device.swipe(device.displayWidth / 2, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight * 4 / 5, 60) }
            device.waitForIdle(1_000); SystemClock.sleep(500)
            if (Build.VERSION.SDK_INT >= 33) instrumentation.getUiAutomation(androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags).clearCache()
        }
        fun screenshot(name: String) {
            instrumentation.waitForIdleSync(); device.waitForIdle(1_000); SystemClock.sleep(500)
            assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "migration-$name.png")))
        }
        fun hideKeyboard() {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { activity ->
                    activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                }
            }
            device.waitForIdle(1_000)
        }
        fun name(value: String) {
            assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5_000))
            device.findObjects(By.clazz("android.widget.EditText")).first().text = value
            hideKeyboard()
        }
        fun findWeb(view: android.view.View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
            return null
        }
        try {
            instrumentation.runOnMainSync { FocusController.pauseRule() }
            assertTrue(onboarding.edit().clear().commit()); assertTrue(rules.edit().clear().commit())
            waitFor("Cleared rule store") { FocusController.ruleStore.rules.value.isEmpty() }
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(ProAccessGate.EXTRA_DEBUG_PRO, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            scrollTo(label(R.string.editor_apps_title))
            click(label(R.string.editor_apps_choose))
            click("Focus test app"); click(label(R.string.done))
            click(label(R.string.onboarding_continue))
            click(label(R.string.m_choose_place))
            // Embedded map renders immediately without a "Show map" wall.
            assertTrue(device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 10_000))
            val attribution = java.util.concurrent.atomic.AtomicReference("")
            waitFor("Leaflet attribution loaded") {
                val checked = java.util.concurrent.CountDownLatch(1)
                instrumentation.runOnMainSync {
                    val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                    val web = activity?.let { findWeb(it.window.decorView) }
                    if (web == null) checked.countDown()
                    else web.evaluateJavascript("document.querySelector('.leaflet-control-attribution')?.innerText || ''") { text ->
                        attribution.set(text); checked.countDown()
                    }
                }
                checked.await(2, java.util.concurrent.TimeUnit.SECONDS)
                attribution.get().contains("Leaflet") && attribution.get().contains("OpenStreetMap")
            }
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                activity?.let { findWeb(it.window.decorView) }?.evaluateJavascript("center(37.5665, 126.9780)", null)
            }
            val centered = java.util.concurrent.atomic.AtomicReference("false")
            waitFor("Map crosshair and 150m circle match Seoul coordinates") {
                val checked = java.util.concurrent.CountDownLatch(1)
                instrumentation.runOnMainSync {
                    val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                    val web = activity?.let { findWeb(it.window.decorView) }
                    if (web == null) checked.countDown()
                    else web.evaluateJavascript(
                        "Math.abs(map.getCenter().lat-37.5665)<0.0001 && Math.abs(map.getCenter().lng-126.9780)<0.0001 && typeof radiusCircle!=='undefined' && radiusCircle.getRadius()===150",
                    ) { result -> centered.set(result); checked.countDown() }
                }
                checked.await(2, java.util.concurrent.TimeUnit.SECONDS)
                centered.get() == "true"
            }
            val diagnostic = java.util.concurrent.atomic.AtomicReference("")
            val diagnosed = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                val web = activity?.let { findWeb(it.window.decorView) }
                if (web == null) diagnosed.countDown()
                else web.evaluateJavascript("JSON.stringify({center:map.getCenter(),map:document.getElementById('map').getBoundingClientRect().toJSON()})") { result ->
                    diagnostic.set(result); diagnosed.countDown()
                }
            }
            diagnosed.await(2, java.util.concurrent.TimeUnit.SECONDS)
            screenshot("map")
            val mapState = org.json.JSONObject(org.json.JSONTokener(diagnostic.get()).nextValue() as String)
            assertTrue("Map container must have visible height", mapState.getJSONObject("map").getDouble("height") > 100)
            top(); click(label(R.string.done))
            name("UI migration rule")
            screenshot("editor"); click(label(R.string.m_save_rule))
            waitFor("Rule saved") { FocusController.ruleStore.rules.value.size == 1 }
            waitFor("Onboarding completed") { OnboardingProfile.completed(context) }
            val saved = FocusController.ruleStore.rules.value.single()
            assertEquals("UI migration rule", saved.name)
            assertEquals(setOf(instrumentation.context.packageName), saved.packages)
            assertEquals(37.5665, saved.latitude, 0.0001); assertEquals(126.9780, saved.longitude, 0.0001)
            assertNull("Saving is not applying", FocusController.state.value.appliedRule)
            // First-run save when accessibility is disconnected lands on setup; Not now continues to Home.
            if (device.wait(Until.hasObject(By.text(label(R.string.not_now))), 3_000)) {
                click(label(R.string.not_now))
            }
            click(label(R.string.m_apply))
            waitFor("Explicit apply") { FocusController.state.value.appliedRule?.id == saved.id }
            assertNull("Applying is not starting focus", FocusController.state.value.session)
            click(label(R.string.m_edit))
            click(label(R.string.onboarding_continue))
            name("Updated UI migration rule"); click(label(R.string.m_save_rule))
            waitFor("Draft persisted separately") { FocusController.ruleStore.hasUnappliedChanges(saved.id) }
            assertEquals("UI migration rule", FocusController.state.value.appliedRule?.name)
            scrollTo(label(R.string.m_unapplied)); screenshot("unapplied")
            click(label(R.string.m_apply))
            waitFor("Edited rule explicitly applied") { FocusController.state.value.appliedRule?.name == "Updated UI migration rule" }
            assertFalse(FocusController.ruleStore.hasUnappliedChanges(saved.id))
            scrollTo(label(R.string.m_pause))
            waitFor("Unapplied warning disappears after applying") {
                if (Build.VERSION.SDK_INT >= 33) instrumentation.getUiAutomation(androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags).clearCache()
                !device.hasObject(By.text(label(R.string.m_unapplied)))
            }
            top(); screenshot("home")
            clickSelector(By.desc(label(R.string.audit_settings)), label(R.string.audit_settings))
            click(label(R.string.m_journal))
            scrollTo(label(R.string.journal_intro)); screenshot("journal")
            click(label(R.string.journal_back))
            device.pressBack()
            top(); screenshot("before-account")
            clickSelector(By.desc(label(R.string.m_account)), label(R.string.m_account))
            screenshot("after-account-click")
            scrollTo(label(R.string.account_title)); screenshot("account")
            click(label(R.string.account_done))
        } finally {
            ProAccessGate.debugProOverride = null
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finish() }
                FocusController.pauseRule()
            }
            restore(onboarding, originalOnboarding); restore(rules, originalRules)
            if (originalApplied != null) {
                waitFor("Restored rules") { FocusController.ruleStore.rules.value.any { it.id == originalApplied.id } }
                instrumentation.runOnMainSync {
                    assertTrue(FocusController.ruleStore.save(originalApplied))
                    assertTrue(FocusController.activateRule(originalApplied.id))
                }
                restore(rules, originalRules)
            }
        }
    }

    @Test fun koreanLargeFontHomeAndAccount() {
        assumeTrue("Isolated API33+ emulator only", Build.VERSION.SDK_INT >= 33 && (Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val locales = context.getSystemService(android.app.LocaleManager::class.java)
        val originalLocales = locales.applicationLocales
        val originalScale = android.provider.Settings.System.getString(context.contentResolver, "font_scale")
        val onboarding = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        val previous = onboarding.all.toMap()
        fun launch() {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(ProAccessGate.EXTRA_DEBUG_PRO, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
        fun label(id: Int): String {
            val configuration = android.content.res.Configuration(context.resources.configuration)
            configuration.setLocale(java.util.Locale.KOREAN)
            return context.createConfigurationContext(configuration).getString(id)
        }
        try {
            assertTrue(onboarding.edit().putBoolean("completed", true).commit())
            instrumentation.runOnMainSync { locales.applicationLocales = android.os.LocaleList.forLanguageTags("ko") }
            device.executeShellCommand("settings put system font_scale 1.5")
            launch()
            var scaled = false
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (!scaled && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    scaled = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any {
                        kotlin.math.abs(it.resources.configuration.fontScale - 1.5f) < 0.01f && it.resources.configuration.locales[0].language == "ko"
                    }
                }
                if (!scaled) SystemClock.sleep(100)
            }
            assertTrue("Korean and 1.5x font actually applied", scaled)
            if (Build.VERSION.SDK_INT >= 33) instrumentation.getUiAutomation(androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags).clearCache()
            assertTrue(device.wait(Until.hasObject(By.desc(label(R.string.m_account))), 10_000))
            assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "migration-home-ko-large.png")))
            device.findObject(By.desc(label(R.string.m_account))).click()
            if (Build.VERSION.SDK_INT >= 33) instrumentation.getUiAutomation(androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags).clearCache()
            assertTrue(device.wait(Until.hasObject(By.text(label(R.string.account_title))), 10_000))
            assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "migration-account-ko-large.png")))
        } finally {
            ProAccessGate.debugProOverride = null
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finish() }
                locales.applicationLocales = originalLocales
            }
            val scale = originalScale?.toFloatOrNull()
            device.executeShellCommand(if (scale == null) "settings delete system font_scale" else "settings put system font_scale $scale")
            restore(onboarding, previous)
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
