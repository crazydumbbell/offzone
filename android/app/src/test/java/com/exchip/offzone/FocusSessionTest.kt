package com.exchip.offzone

import org.junit.Assert.*
import org.junit.Test

class FocusSessionTest {
    @Test fun sessionExpiresAndProtectsRecoveryApps() {
        val selected = mutableSetOf("example.distraction", "example.dialer")
        val session = FocusSession.start(selected, 1, 1_000)
        selected.clear()
        assertTrue(session.blocks("example.distraction", 60_999, emptySet()))
        assertFalse(session.blocks("example.distraction", 61_000, emptySet()))
        assertFalse(session.blocks("example.dialer", 2_000, setOf("example.dialer")))
        assertFalse(session.blocks("example.other", 2_000, emptySet()))
        assertEquals(0L, session.remaining(999))
        assertEquals(0L, session.remaining(Long.MAX_VALUE))
        listOf(0, -1, 2, Int.MAX_VALUE).forEach { duration ->
            assertThrows(IllegalArgumentException::class.java) { FocusSession.start(setOf("a"), duration, 0) }
        }
        assertThrows(IllegalArgumentException::class.java) { FocusSession.start(emptySet(), 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { FocusSession.start(setOf(""), 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { FocusSession.start(setOf("a"), 1, Long.MAX_VALUE) }
    }
}
