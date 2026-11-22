package com.exchip.offzone

import org.junit.Assert.*
import org.junit.Test

class FocusSessionTest {
    @Test fun sessionExpiresAndProtectsRecoveryApps() {
        assertEquals(listOf(15, 25, 50), FocusSession.durations)
        val selected = mutableSetOf("example.distraction", "example.dialer")
        val session = FocusSession.start(selected, 15, 1_000)
        selected.clear()
        assertTrue(session.blocks("example.distraction", 900_999, emptySet()))
        assertFalse(session.blocks("example.distraction", 901_000, emptySet()))
        assertFalse(session.blocks("example.dialer", 2_000, setOf("example.dialer")))
        assertFalse(session.blocks("example.other", 2_000, emptySet()))
        assertEquals(0L, session.remaining(999))
        assertEquals(0L, session.remaining(Long.MAX_VALUE))
        listOf(0, -1, 1, 2, Int.MAX_VALUE).forEach { duration ->
            assertThrows(IllegalArgumentException::class.java) { FocusSession.start(setOf("a"), duration, 0) }
        }
        assertThrows(IllegalArgumentException::class.java) { FocusSession.start(emptySet(), 15, 0) }
        assertThrows(IllegalArgumentException::class.java) { FocusSession.start(setOf(""), 15, 0) }
        assertThrows(IllegalArgumentException::class.java) { FocusSession.start(setOf("a"), 15, Long.MAX_VALUE) }
    }
}
