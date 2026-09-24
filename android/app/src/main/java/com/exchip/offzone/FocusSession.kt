package com.exchip.offzone

/** Uses monotonic time so changing the wall clock cannot extend a session. */
data class FocusSession(val packages: Set<String>, val startedAt: Long, val endsAt: Long) {
    fun remaining(now: Long): Long = if (now < startedAt) 0 else (endsAt - now).coerceAtLeast(0)
    fun blocks(packageName: String, now: Long, protected: Set<String>): Boolean =
        remaining(now) > 0 && packageName in packages && packageName !in protected

    companion object {
        val durations = listOf(1, 15, 25, 50)
        fun start(packages: Set<String>, minutes: Int, now: Long): FocusSession {
            require(packages.isNotEmpty() && packages.none { it.isBlank() })
            require(minutes in durations && now >= 0 && now <= Long.MAX_VALUE - minutes * 60_000L)
            return FocusSession(packages.toSet(), now, now + minutes * 60_000L)
        }
    }
}
