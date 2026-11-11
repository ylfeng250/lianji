package com.superman.lianji.core.domain

data class RestSnapshot(
    val deadlineElapsedRealtime: Long,
    val durationSeconds: Int,
    val wallDeadlineEpochMs: Long,
    val bootCount: Int,
    val sessionId: String,
    val exerciseName: String,
)

fun remainingMillis(snapshot: RestSnapshot, nowElapsed: Long, nowWall: Long, bootCount: Int): Long {
    val raw = if (bootCount == snapshot.bootCount) {
        snapshot.deadlineElapsedRealtime - nowElapsed
    } else {
        snapshot.wallDeadlineEpochMs - nowWall
    }
    return raw.coerceAtLeast(0L)
}
