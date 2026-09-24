package com.exchip.offzone

import org.junit.Assert.*
import org.junit.Test
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
}
