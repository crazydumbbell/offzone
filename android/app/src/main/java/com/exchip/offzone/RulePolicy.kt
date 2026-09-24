package com.exchip.offzone

import java.time.ZonedDateTime
import java.util.UUID

data class FocusRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packages: Set<String>,
    val startMinutes: Int,
    val endMinutes: Int,
    val latitude: Double,
    val longitude: Double,
    val placeLabel: String = "",
) {
    fun valid(): Boolean = id.isNotBlank() && name.isNotBlank() && name.length <= 120 &&
        packages.isNotEmpty() && packages.none { it.isBlank() } &&
        startMinutes in 0..1439 && endMinutes in 0..1439 &&
        RulePolicy.intervalMinutes(startMinutes, endMinutes) >= 15 &&
        latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0
}

object RulePolicy {
    fun intervalMinutes(start: Int, end: Int) = (end - start + 1440) % 1440
    fun scheduleActive(minute: Int, start: Int, end: Int): Boolean =
        minute in 0..1439 && start in 0..1439 && end in 0..1439 && start != end &&
            if (start < end) minute >= start && minute < end else minute >= start || minute < end
    fun scheduleActive(rule: FocusRule, now: ZonedDateTime = ZonedDateTime.now()) =
        scheduleActive(now.hour * 60 + now.minute, rule.startMinutes, rule.endMinutes)
    fun scheduleEnd(rule: FocusRule, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        var end = now.toLocalDate().atTime(rule.endMinutes / 60, rule.endMinutes % 60).atZone(now.zone)
        if (!end.isAfter(now)) end = now.toLocalDate().plusDays(1)
            .atTime(rule.endMinutes / 60, rule.endMinutes % 60).atZone(now.zone)
        return end
    }
    /** Boundary hysteresis can retain presence, but cannot invent an arrival. */
    fun presence(distance: Double, accuracy: Double, ageMillis: Long, wasInside: Boolean): Boolean? {
        if (!distance.isFinite() || distance < 0 || !accuracy.isFinite() || accuracy !in 0.0..100.0 || ageMillis !in 0..30_000) return null
        if (distance + accuracy <= 150) return true
        if (distance - accuracy > 200) return false
        return wasInside
    }
}
