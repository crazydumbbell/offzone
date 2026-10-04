package com.exchip.offzone

import java.time.DayOfWeek
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
    /** Bit (DayOfWeek.value - 1). An overnight occurrence belongs to the day it starts. */
    val days: Int = RulePolicy.EVERY_DAY,
) {
    fun valid(): Boolean = id.isNotBlank() && name.isNotBlank() && name.length <= 120 &&
        packages.isNotEmpty() && packages.none { it.isBlank() } &&
        startMinutes in 0..1439 && endMinutes in 0..1439 &&
        RulePolicy.intervalMinutes(startMinutes, endMinutes) >= 15 && days in 1..RulePolicy.EVERY_DAY &&
        latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0
}

object RulePolicy {
    const val EVERY_DAY = 0x7F
    const val WEEKDAYS = 0x1F
    fun dayOn(days: Int, day: DayOfWeek) = days and (1 shl (day.value - 1)) != 0
    fun intervalMinutes(start: Int, end: Int) = (end - start + 1440) % 1440
    fun scheduleActive(minute: Int, start: Int, end: Int): Boolean =
        minute in 0..1439 && start in 0..1439 && end in 0..1439 && start != end &&
            if (start < end) minute >= start && minute < end else minute >= start || minute < end
    fun scheduleActive(rule: FocusRule, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        val minute = now.hour * 60 + now.minute
        if (!scheduleActive(minute, rule.startMinutes, rule.endMinutes)) return false
        val carried = rule.startMinutes > rule.endMinutes && minute < rule.endMinutes
        return dayOn(rule.days, if (carried) now.dayOfWeek.minus(1) else now.dayOfWeek)
    }
    fun nextStart(rule: FocusRule, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? =
        (0L..7L).map { now.toLocalDate().plusDays(it) }.filter { dayOn(rule.days, it.dayOfWeek) }
            .map { it.atTime(rule.startMinutes / 60, rule.startMinutes % 60).atZone(now.zone) }
            .firstOrNull { it.isAfter(now) }
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
