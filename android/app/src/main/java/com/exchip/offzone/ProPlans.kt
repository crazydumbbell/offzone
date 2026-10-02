package com.exchip.offzone

import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.models.Period
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** One plan in the Pro funnel. Prices, periods and trials come from the store response and are never written in code (D-49). */
data class ProPlan(
    val id: String, val annual: Boolean, val price: String, val micros: Long, val currency: String,
    val trialDays: Int?, val pkg: Package? = null,
)

internal object ProPlans {
    fun days(period: Period): Int = period.value * when (period.unit) {
        Period.Unit.DAY -> 1; Period.Unit.WEEK -> 7; Period.Unit.MONTH -> 30; Period.Unit.YEAR -> 365; else -> 0
    }

    fun of(pkg: Package): ProPlan {
        val option = pkg.product.defaultOption
        val price = option?.fullPricePhase?.price ?: pkg.product.price
        return ProPlan(pkg.identifier, pkg.packageType == PackageType.ANNUAL, price.formatted, price.amountMicros, price.currencyCode,
            option?.freePhase?.billingPeriod?.let(::days)?.takeIf { it > 0 }, pkg)
    }

    /** Money in a plan's own currency; null when the currency code is unknown. */
    fun money(micros: Long, currency: String, locale: Locale = Locale.getDefault()): String? = runCatching {
        val code = Currency.getInstance(currency)
        // setCurrency keeps the locale's own digits (0 for Korean won), which would print US$2 for 2.50 dollars.
        NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = code
            minimumFractionDigits = code.defaultFractionDigits.coerceAtLeast(0); maximumFractionDigits = minimumFractionDigits
        }.format(micros / 1_000_000.0)
    }.getOrNull()

    fun perMonth(plan: ProPlan, locale: Locale = Locale.getDefault()) = money(plan.micros / 12, plan.currency, locale)

    /** Percent saved by paying yearly instead of twelve months, rounded down. Null unless both plans share a currency and yearly is cheaper. */
    fun savingsPercent(yearly: ProPlan?, monthly: ProPlan?): Int? {
        if (yearly == null || monthly == null || yearly.currency != monthly.currency || monthly.micros <= 0) return null
        val full = monthly.micros * 12
        return if (yearly.micros < full) ((full - yearly.micros) * 100 / full).toInt() else null
    }

    /** The plan that is preselected, and whose trial the intro steps talk about: a plan with a store trial first (monthly wins a tie, D-50), then yearly, then the first plan. */
    fun defaultPlan(plans: List<ProPlan>): ProPlan? {
        val trials = plans.filter { it.trialDays != null }
        return trials.firstOrNull { !it.annual } ?: trials.firstOrNull() ?: plans.firstOrNull { it.annual } ?: plans.firstOrNull()
    }

    /** Cards in display order: the default plan on top, the others after it. */
    fun ordered(plans: List<ProPlan>): List<ProPlan> {
        val first = defaultPlan(plans) ?: return plans
        return listOf(first) + plans.filter { it.id != first.id }
    }

    /** Timeline days: today, one reminder day when the trial is long enough, and the day the trial ends. */
    fun timelineDays(trialDays: Int): List<Int> = if (trialDays >= 2) listOf(0, trialDays - 1, trialDays) else listOf(0, trialDays)
}
