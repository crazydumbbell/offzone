package com.exchip.offzone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptTest {
    @Test
    fun triggersOnSecondMomentThenRespectsCooldownAndDelta() {
        val day0 = 1_700_000_000_000L
        val day = 86_400_000L

        assertFalse(ReviewPromptPolicy.shouldPrompt(1, null, 0, day0))
        assertTrue(ReviewPromptPolicy.shouldPrompt(2, null, 0, day0))

        // Inside 60-day cooldown: blocked even with many new moments.
        assertFalse(ReviewPromptPolicy.shouldPrompt(10, day0, 2, day0 + 59 * day))

        // Past 60-day cooldown, but fewer than 3 new moments since last prompt: still blocked.
        assertFalse(ReviewPromptPolicy.shouldPrompt(4, day0, 2, day0 + 61 * day))

        // Past 60-day cooldown and at least 3 new moments: eligible again.
        assertTrue(ReviewPromptPolicy.shouldPrompt(5, day0, 2, day0 + 61 * day))
    }
}
