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
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {
    @Test fun englishStoreScreens() {
        assumeTrue("Isolated emulator only",Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val device=UiDevice.getInstance(instrumentation)
        // This dedicated emulator occasionally opens a System UI ANR before our app launches.
        // Dismiss only that known system-process modal; never hide an Offzone ANR.
        if (device.hasObject(By.textContains("System UI"))) {
            device.findObject(By.text("Close app"))?.click()
            device.waitForIdle(1_000)
        }
        val onboarding=context.getSharedPreferences("onboarding",Context.MODE_PRIVATE)
        val rules=context.getSharedPreferences("rules",Context.MODE_PRIVATE)
        val originalOnboarding=onboarding.all.toMap(); val originalRules=rules.all.toMap()
        val focus=AppSelection.preferences(context)
        val originalFocus=focus.all.toMap()
        val services=android.provider.Settings.Secure.getString(context.contentResolver,android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val accessibility=android.provider.Settings.Secure.getInt(context.contentResolver,android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,0)
        val service=android.content.ComponentName(context,FocusAccessibilityService::class.java).flattenToString()
        androidx.test.uiautomator.Configurator.getInstance().setUiAutomationFlags(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        instrumentation.runOnMainSync { FocusController.initialize(context) }
        assumeTrue("Do not interrupt existing focus",FocusController.state.value.session==null)
        val originalApplied=FocusController.state.value.appliedRule
        fun label(id: Int)=context.getString(id)
        fun waitFor(description: String, condition: ()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+10_000
            while(!condition() && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(100)
            assertTrue(description,condition())
        }
        fun scrollTo(text: String) {
            repeat(9) {
                if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
                if(device.wait(Until.hasObject(By.text(text)),700)) return
                device.swipe(device.displayWidth/2,device.displayHeight*4/5,device.displayWidth/2,device.displayHeight/3,60)
                device.waitForIdle(1_000); SystemClock.sleep(300)
            }
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null),"migration-failure.xml"))
            device.takeScreenshot(File(context.getExternalFilesDir(null),"migration-failure.png"))
            fail("Missing UI text: $text")
        }
        fun click(text: String) {
            scrollTo(text)
            device.waitForIdle(1_000); SystemClock.sleep(300)
            if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            val target = device.wait(Until.findObject(By.text(text)), 5_000)
            assertNotNull("Action still visible: $text", target)
            target!!.click(); device.waitForIdle(1_000)
            if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        }
        fun top() {
            repeat(5) { device.swipe(device.displayWidth/2,device.displayHeight/3,device.displayWidth/2,device.displayHeight*4/5,60) }
            device.waitForIdle(1_000); SystemClock.sleep(500)
            if(Build.VERSION.SDK_INT>=33) instrumentation.uiAutomation.clearCache()
        }
        fun screenshot(name: String) {
            instrumentation.waitForIdleSync(); device.waitForIdle(1_000); SystemClock.sleep(500)
            assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null),"store-$name.png")))
        }
        fun hideKeyboard() {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { activity ->
                    activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .hideSoftInputFromWindow(activity.window.decorView.windowToken,0)
                }
            }
            device.waitForIdle(1_000)
        }
        fun name(value: String) {
            assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")),5_000))
            device.findObjects(By.clazz("android.widget.EditText")).first().text=value
            hideKeyboard()
        }
        try {
            instrumentation.runOnMainSync { FocusController.pauseRule() }
            assertTrue(onboarding.edit().clear().commit()); assertTrue(rules.edit().clear().commit())
            waitFor("Cleared rule store") { FocusController.ruleStore.rules.value.isEmpty() }
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            scrollTo(label(R.string.onboarding_start)); screenshot("welcome")
            click(label(R.string.onboarding_start)); click(label(R.string.goal_presence)); click(label(R.string.onboarding_continue))
            click(label(R.string.onboarding_evening_title)); screenshot("onboarding"); click(label(R.string.onboarding_done))
            waitFor("Onboarding persisted") { OnboardingProfile.completed(context) }
            assertEquals("presence",OnboardingProfile.goal(context)); assertEquals("evening",OnboardingProfile.window(context))
            scrollTo(label(R.string.editor_apps_title))
            click(label(R.string.editor_apps_choose))
            click("Chrome"); click(label(R.string.done))
            click(label(R.string.editor_next_place))
            click(label(R.string.m_choose_place))
            scrollTo(label(R.string.m_latitude))
            var fields=device.findObjects(By.clazz("android.widget.EditText")).sortedBy { it.visibleBounds.top }
            assertEquals("Search, latitude and longitude fields",3,fields.size)
            fields[1].text="51.5080"
            fields=device.findObjects(By.clazz("android.widget.EditText")).sortedBy { it.visibleBounds.top }
            fields.last().text="-0.1280"
            hideKeyboard(); screenshot("place")
            top(); click(label(R.string.m_show_map))
            assertTrue(device.wait(Until.hasObject(By.clazz("android.webkit.WebView")),10_000))
            val attribution=java.util.concurrent.atomic.AtomicReference<String>("")
            fun findWeb(view: android.view.View): android.webkit.WebView? {
                if (view is android.webkit.WebView) return view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
                return null
            }
            waitFor("Leaflet attribution loaded") {
                val checked=java.util.concurrent.CountDownLatch(1)
                instrumentation.runOnMainSync {
                    val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                    val web=activity?.let { findWeb(it.window.decorView) }
                    if (web==null) checked.countDown() else web.evaluateJavascript("document.querySelector('.leaflet-control-attribution')?.innerText || ''") { text -> attribution.set(text); checked.countDown() }
                }
                checked.await(2,java.util.concurrent.TimeUnit.SECONDS)
                attribution.get().contains("Leaflet") && attribution.get().contains("OpenStreetMap")
            }
            val centered=java.util.concurrent.atomic.AtomicReference<String>("false")
            waitFor("Map crosshair matches the selected London coordinates") {
                val checked=java.util.concurrent.CountDownLatch(1)
                instrumentation.runOnMainSync {
                    val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                    val web=activity?.let { findWeb(it.window.decorView) }
                    if(web==null) checked.countDown() else web.evaluateJavascript("Math.abs(map.getCenter().lat-51.5080)<0.0001 && Math.abs(map.getCenter().lng-(-0.1280))<0.0001") { result -> centered.set(result); checked.countDown() }
                }
                checked.await(2,java.util.concurrent.TimeUnit.SECONDS)
                centered.get()=="true"
            }
            val tiles=java.util.concurrent.atomic.AtomicReference<String>("false")
            val tileDeadline=SystemClock.elapsedRealtime()+10_000
            while(tiles.get()!="true" && SystemClock.elapsedRealtime()<tileDeadline) {
                val checked=java.util.concurrent.CountDownLatch(1)
                instrumentation.runOnMainSync {
                    val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                    val web=activity?.let { findWeb(it.window.decorView) }
                    if(web==null) checked.countDown() else web.evaluateJavascript("document.querySelectorAll('img.leaflet-tile-loaded').length > 0") { result -> tiles.set(result); checked.countDown() }
                }
                checked.await(2,java.util.concurrent.TimeUnit.SECONDS)
                if(tiles.get()!="true") SystemClock.sleep(200)
            }
            val diagnostic=java.util.concurrent.atomic.AtomicReference<String>("")
            val diagnosed=java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                val web=activity?.let { findWeb(it.window.decorView) }
                if(web==null) diagnosed.countDown() else web.evaluateJavascript("JSON.stringify({center:map.getCenter(),sheets:document.styleSheets.length,css:[...document.styleSheets].map(s=>s.href),map:document.getElementById('map').getBoundingClientRect().toJSON(),tiles:document.querySelectorAll('img.leaflet-tile').length,loaded:document.querySelectorAll('img.leaflet-tile-loaded').length,failed:[...document.querySelectorAll('img.leaflet-tile')].filter(i=>i.complete&&!i.naturalWidth).length})") { result -> diagnostic.set(result); diagnosed.countDown() }
            }
            diagnosed.await(2,java.util.concurrent.TimeUnit.SECONDS)
            File(context.getExternalFilesDir(null),"migration-map-status.txt").writeText("attribution=${attribution.get()}\ntilesLoaded=${tiles.get()}\ndiagnostic=${diagnostic.get()}\n")
            screenshot("map")
            val mapState=org.json.JSONObject(org.json.JSONTokener(diagnostic.get()).nextValue() as String)
            assertTrue("Map container must have visible height",mapState.getJSONObject("map").getDouble("height")>100)
            top(); click(label(R.string.done))
            click(label(R.string.editor_next_time))
            click(label(R.string.editor_next_review))
            name("Quiet evenings")
            screenshot("editor"); click(label(R.string.m_save_rule))
            waitFor("Rule saved") { FocusController.ruleStore.rules.value.size==1 }
            val saved=FocusController.ruleStore.rules.value.single()
            assertEquals("Quiet evenings",saved.name)
            assertEquals(setOf("com.android.chrome"),saved.packages)
            assertEquals(1200,saved.startMinutes); assertEquals(1260,saved.endMinutes)
            assertEquals(51.5080,saved.latitude,0.00001); assertEquals(-0.1280,saved.longitude,0.00001)
            assertNull("Saving is not applying",FocusController.state.value.appliedRule)
            click(label(R.string.ready_go_home))
            focus.edit().putBoolean("disclosure",true).commit()
            device.executeShellCommand("settings put secure enabled_accessibility_services ${(services.split(':').filter { it.isNotBlank() } + service).distinct().joinToString(":")}")
            device.executeShellCommand("settings put secure accessibility_enabled 1")
            waitFor("Accessibility service connected") { FocusController.state.value.connected }
            click(label(R.string.m_apply))
            waitFor("Explicit apply") { FocusController.state.value.appliedRule?.id==saved.id }
            assertNull("Applying is not starting focus",FocusController.state.value.session)
            click(label(R.string.m_edit))
            click(label(R.string.editor_next_place)); click(label(R.string.editor_next_time)); click(label(R.string.editor_next_review))
            name("Evening focus"); click(label(R.string.m_save_rule))
            waitFor("Draft persisted separately") { FocusController.ruleStore.hasUnappliedChanges(saved.id) }
            assertEquals("Quiet evenings",FocusController.state.value.appliedRule?.name)
            scrollTo(label(R.string.m_unapplied)); screenshot("unapplied")
            click(label(R.string.m_apply))
            waitFor("Edited rule explicitly applied") { FocusController.state.value.appliedRule?.name=="Evening focus" }
            assertFalse(FocusController.ruleStore.hasUnappliedChanges(saved.id))
            scrollTo(label(R.string.m_pause))
            waitFor("Unapplied warning disappears after applying") {
                if(Build.VERSION.SDK_INT>=33) instrumentation.uiAutomation.clearCache()
                !device.hasObject(By.text(label(R.string.m_unapplied)))
            }
            top(); screenshot("home")
            click(label(R.string.m_journal))
            scrollTo(label(R.string.journal_intro)); screenshot("journal")
            click(label(R.string.journal_back)); top(); screenshot("before-account"); click(label(R.string.m_account))
            screenshot("after-account-click")
            scrollTo(label(R.string.account_title)); screenshot("account")
            click(label(R.string.account_done))
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finish() }
                FocusController.pauseRule()
            }
            device.executeShellCommand(if(services.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $services")
            device.executeShellCommand("settings put secure accessibility_enabled $accessibility")
            restore(focus,originalFocus)
            restore(onboarding,originalOnboarding); restore(rules,originalRules)
            if(originalApplied!=null) {
                waitFor("Restored rules") { FocusController.ruleStore.rules.value.any { it.id==originalApplied.id } }
                instrumentation.runOnMainSync {
                    assertTrue(FocusController.ruleStore.save(originalApplied))
                    assertTrue(FocusController.activateRule(originalApplied.id))
                }
                // Restore the exact persisted snapshot if a draft differed from the applied rule.
                restore(rules,originalRules)
            }
        }
    }
    private fun restore(prefs: SharedPreferences, values: Map<String,*>) {
        val editor=prefs.edit().clear()
        values.forEach { (key,value) -> when(value) {
            is String -> editor.putString(key,value)
            is Boolean -> editor.putBoolean(key,value)
            is Int -> editor.putInt(key,value)
            is Long -> editor.putLong(key,value)
            is Float -> editor.putFloat(key,value)
            is Set<*> -> editor.putStringSet(key,value.filterIsInstance<String>().toSet())
        } }
        assertTrue(editor.commit())
    }
}
