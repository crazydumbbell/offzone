package com.exchip.offzone

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class RulePolicyTest {
    @Test fun schedulesPresenceAndValidationFailOpenAtBoundaries() {
        assertTrue(RulePolicy.scheduleActive(23 * 60, 22 * 60, 6 * 60))
        assertTrue(RulePolicy.scheduleActive(0, 22 * 60, 6 * 60))
        assertFalse(RulePolicy.scheduleActive(6 * 60, 22 * 60, 6 * 60))
        assertFalse(RulePolicy.scheduleActive(600, 600, 600))
        assertFalse(RulePolicy.scheduleActive(600, -1, 800))
        assertFalse(RulePolicy.scheduleActive(600, 500, 1440))
        assertEquals(480, RulePolicy.intervalMinutes(1320, 360))
        assertEquals(true, RulePolicy.presence(130.0, 20.0, 30_000, false))
        assertEquals(false, RulePolicy.presence(131.0, 20.0, 0, false))
        assertEquals(true, RulePolicy.presence(180.0, 20.0, 0, true))
        assertEquals(false, RulePolicy.presence(221.0, 20.0, 0, true))
        assertNull(RulePolicy.presence(0.0, 20.0, 30_001, true))
        assertNull(RulePolicy.presence(0.0, 20.0, -1, true))
        assertNull(RulePolicy.presence(0.0, 101.0, 0, true))
        assertNull(RulePolicy.presence(Double.NaN, 20.0, 0, true))
        val rule = FocusRule(name = "Night", packages = setOf("example.video"), startMinutes = 1320, endMinutes = 360, latitude = 37.0, longitude = 127.0)
        assertTrue(rule.valid())
        assertFalse(rule.copy(endMinutes = 1325).valid())
        assertFalse(rule.copy(latitude = Double.NaN).valid())
        assertFalse(rule.copy(packages = emptySet()).valid())
        val now = ZonedDateTime.of(2026, 9, 24, 23, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        assertEquals(now.toLocalDate().plusDays(1), RulePolicy.scheduleEnd(rule, now).toLocalDate())
        assertEquals(6, RulePolicy.scheduleEnd(rule, now).hour)
        val spring = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, ZoneId.of("America/New_York"))
        assertTrue(RulePolicy.scheduleEnd(rule.copy(endMinutes = 150), spring).isAfter(spring))
    }

    @Test fun weekdaysBelongToTheStartDayAndOldRulesRunDaily() {
        val zone = ZoneId.of("Asia/Seoul")
        val friday = ZonedDateTime.of(2026, 9, 25, 23, 0, 0, 0, zone)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        val night = FocusRule(name = "Night", packages = setOf("example.video"), startMinutes = 22 * 60, endMinutes = 2 * 60,
            latitude = 37.0, longitude = 127.0, days = 1 shl (DayOfWeek.FRIDAY.value - 1))
        assertTrue(RulePolicy.scheduleActive(night, friday))
        assertTrue(RulePolicy.scheduleActive(night, friday.plusHours(2))) // Saturday 01:00 continues Friday.
        assertFalse(RulePolicy.scheduleActive(night, friday.plusHours(3)))
        assertFalse(RulePolicy.scheduleActive(night, friday.plusDays(1))) // Saturday 23:00 is not selected.
        assertFalse(RulePolicy.scheduleActive(night, friday.minusDays(1).minusHours(21))) // Thursday 02:00 continues Wednesday.
        assertEquals(friday.plusDays(7).withHour(22), RulePolicy.nextStart(night, friday))
        assertEquals(friday.withHour(22), RulePolicy.nextStart(night, friday.withHour(9)))
        assertFalse(night.copy(days = 0).valid())
        assertFalse(night.copy(days = 128).valid())
        val daily = night.copy(days = RulePolicy.EVERY_DAY)
        assertEquals(RulePolicy.EVERY_DAY, FocusRule(name = "Old", packages = setOf("a"), startMinutes = 0, endMinutes = 60, latitude = 0.0, longitude = 0.0).days)
        assertTrue((0L..6L).all { RulePolicy.scheduleActive(daily, friday.plusDays(it)) })
        val weekdays = daily.copy(startMinutes = 9 * 60, endMinutes = 17 * 60, days = RulePolicy.WEEKDAYS)
        assertTrue(RulePolicy.scheduleActive(weekdays, friday.withHour(10)))
        assertFalse(RulePolicy.scheduleActive(weekdays, friday.plusDays(1).withHour(10)))
        assertEquals(DayOfWeek.MONDAY, RulePolicy.nextStart(weekdays, friday)!!.dayOfWeek)
    }
}
