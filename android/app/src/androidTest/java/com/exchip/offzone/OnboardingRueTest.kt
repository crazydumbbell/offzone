package com.exchip.offzone

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingRueTest {
    @Test fun onboardingPersistsOnlyValidSelections() {
        // Instrumentation runs with the target UID, so test-APK storage is not writable.
        // A distinct target-owned preferences file keeps real onboarding unchanged.
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val file="onboarding-test-${System.nanoTime()}"
        fun isolated(base: Context) = object: android.content.ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(file,mode)
        }
        val context=isolated(target)
        try {
            assertFalse(OnboardingProfile.completed(context))
            assertEquals("work",OnboardingProfile.goal(context))
            assertEquals("morning",OnboardingProfile.window(context))
            assertTrue(OnboardingProfile.save(context,"presence","evening"))
            val reopened=isolated(target.createPackageContext(target.packageName,0))
            assertTrue(OnboardingProfile.completed(reopened))
            assertEquals("presence",OnboardingProfile.goal(reopened))
            assertEquals("evening",OnboardingProfile.window(reopened))
            assertTrue(runCatching { OnboardingProfile.save(context,"unknown","evening") }.isFailure)
            assertTrue(runCatching { OnboardingProfile.save(context,"work","noon") }.isFailure)
            assertEquals("presence",OnboardingProfile.goal(context))
            assertEquals("evening",OnboardingProfile.window(context))
        } finally { assertTrue(target.deleteSharedPreferences(file)) }
    }

    @Test fun twelveRueExpressionsDecodeWithAlpha() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(12,RueExpression.entries.size)
        RueExpression.entries.forEach { expression ->
            val drawable=context.getDrawable(expression.asset)
            assertNotNull(drawable)
            assertTrue(drawable!!.intrinsicWidth>0 && drawable.intrinsicHeight>0)
            val image=BitmapFactory.decodeResource(context.resources,expression.asset)
            assertNotNull(image)
            assertTrue(image.hasAlpha())
            assertTrue(android.graphics.Color.alpha(image.getPixel(0,0)) < 8)
            image.recycle()
        }
    }
}
