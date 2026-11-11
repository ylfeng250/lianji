package com.superman.lianji.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingRulesTest {
    @Test
    fun warmupAndUnfinishedSetsDoNotCount() {
        val warmup = sample(kind = SetKind.WARMUP, weight = 100_000, reps = 5)
        val unfinished = sample(completed = false, weight = 100_000, reps = 5)
        assertEquals(0.0, volumeKg(warmup), 0.001)
        assertEquals(0.0, volumeKg(unfinished), 0.001)
        assertNull(estimatedOneRepMaxKg(warmup))
    }

    @Test
    fun epleyUsesWorkingRepetitionsOnly() {
        assertEquals(100.0, estimatedOneRepMaxKg(sample(weight = 100_000, reps = 1))!!, 0.001)
        assertEquals(100.0 * (1.0 + 5.0 / 30.0), estimatedOneRepMaxKg(sample(weight = 100_000, reps = 5))!!, 0.001)
        assertNull(estimatedOneRepMaxKg(sample(weight = 100_000, reps = 11)))
    }

    @Test
    fun assistedWeightIsNotTrainingVolume() {
        val assisted = sample(tracking = TrackingType.ASSISTED, weight = 40_000, reps = 8)
        assertEquals(0.0, volumeKg(assisted), 0.001)
    }

    @Test
    fun streakAllowsTodayToBeEmpty() {
        assertEquals(3, trainingStreak(setOf(10L, 11L, 12L), 13L))
        assertEquals(0, trainingStreak(setOf(10L, 11L), 13L))
        assertEquals(1, trainingStreak(setOf(13L), 13L))
    }

    @Test
    fun weeklyBucketsStartOnMonday() {
        val monday = weekStart(LocalDates.epochDay(2026, 11, 9))
        assertEquals(monday, weekStart(LocalDates.epochDay(2026, 11, 10)))
        val points = weeklyPoints(
            sessions = listOf(SessionMarker("a", monday, 600_000)),
            sets = listOf(sample(epochDay = monday, weight = 50_000, reps = 10)),
            today = monday,
            weeks = 2,
        )
        assertEquals(2, points.size)
        assertEquals(1, points.last().sessions)
        assertEquals(500.0, points.last().volumeKg, 0.001)
    }

    @Test
    fun weightParsingAcceptsChineseDecimalComma() {
        assertEquals(60_500, displayToGrams("60,5", WeightUnit.KG))
        assertEquals("60.5", gramsToInput(60_500, WeightUnit.KG))
    }
}

private object LocalDates {
    fun epochDay(year: Int, month: Int, day: Int) =
        java.time.LocalDate.of(year, month, day).toEpochDay()
}

private fun sample(
    epochDay: Long = 20_000,
    tracking: TrackingType = TrackingType.WEIGHT_REPS,
    kind: SetKind = SetKind.NORMAL,
    completed: Boolean = true,
    weight: Int? = null,
    reps: Int? = null,
) = StatSet(
    sessionId = "session",
    epochDay = epochDay,
    exerciseId = "exercise",
    exerciseName = "杠铃卧推",
    muscleZh = "胸",
    tracking = tracking,
    kind = kind,
    completed = completed,
    reps = reps,
    weightGrams = weight,
    durationMs = null,
    distanceMm = null,
)
