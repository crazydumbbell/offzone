package com.exchip.offzone

import com.revenuecat.purchases.models.Period
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class ProPlansTest {
    private fun plan(micros: Long, annual: Boolean = false, currency: String = "USD") = ProPlan("p", annual, "x", micros, currency, null)

    @Test fun trialLengthAndSavingsComeFromStoreAmounts() {
        assertEquals(3, ProPlans.days(Period(3, Period.Unit.DAY, "P3D")))
        assertEquals(7, ProPlans.days(Period(1, Period.Unit.WEEK, "P1W")))
        assertEquals(49, ProPlans.savingsPercent(plan(29_990_000, true), plan(4_990_000)))   // 29.99 against 12 x 4.99
        assertNull(ProPlans.savingsPercent(plan(60_000_000, true), plan(4_990_000)))          // yearly is not cheaper
        assertNull(ProPlans.savingsPercent(plan(29_990_000, true, "EUR"), plan(4_990_000)))   // never compare two currencies
        assertEquals("\$2.50", ProPlans.perMonth(plan(29_990_000, true), Locale.US))
        assertTrue(ProPlans.perMonth(plan(29_990_000, true), Locale.KOREA)!!.endsWith("2.50"))     // a Korean locale must still show cents for USD
        assertTrue(ProPlans.money(0, "USD", Locale.KOREA)!!.endsWith("0.00"))
        assertEquals("49,000", ProPlans.money(49_000_000_000L, "KRW", Locale.KOREA)!!.filter { it.isDigit() || it == ',' })
        assertEquals(listOf(0, 2, 3), ProPlans.timelineDays(3))
        assertEquals(listOf(0, 1), ProPlans.timelineDays(1))
    }

    @Test fun monthlyTrialLeadsAndYearlyFollows() {
        val monthly = ProPlan("m", false, "x", 4_990_000, "USD", 14)
        val yearly = ProPlan("a", true, "x", 29_990_000, "USD", null)
        assertEquals("m", ProPlans.defaultPlan(listOf(yearly, monthly))?.id)
        assertEquals(listOf("m", "a"), ProPlans.ordered(listOf(yearly, monthly)).map { it.id })
        // The trial is on the monthly plan: the trial then rolls into that monthly price.
        assertEquals(14, ProPlans.ordered(listOf(yearly, monthly)).first().trialDays)
        // Without any trial the yearly plan stays the default, and nothing is lost.
        val plain = ProPlan("m", false, "x", 4_990_000, "USD", null)
        assertEquals("a", ProPlans.defaultPlan(listOf(plain, yearly))?.id)
        assertEquals(setOf("m", "a"), ProPlans.ordered(listOf(plain, yearly)).map { it.id }.toSet())
        assertNull(ProPlans.defaultPlan(emptyList()))
    }

    @Test fun reminderIsOneDayBeforeTheTrialEndsAndSkippedForShortTrials() {
        assertEquals(2 * 86_400_000L + 1_000L, TrialReminder.reminderAt(1_000L, 3))
        assertNull(TrialReminder.reminderAt(1_000L, 1))
    }
}
