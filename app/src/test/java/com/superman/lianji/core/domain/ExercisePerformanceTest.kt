package com.superman.lianji.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePerformanceTest {
    @Test
    fun recentPerformancesKeepsNewestThreeAndSkipsWarmups() {
        val sets = listOf(
            set("s1", day = 100, weight = 50_000, reps = 10),
            set("s1", day = 100, weight = 50_000, reps = 8, kind = SetKind.WARMUP),
            set("s2", day = 101, weight = 55_000, reps = 8),
            set("s3", day = 102, weight = 57_500, reps = 6),
            set("s4", day = 103, weight = 60_000, reps = 5),
            set("s5", day = 104, weight = 62_500, reps = 5, exerciseId = "other"),
        )
        val result = recentPerformances(sets, "exercise")
        assertEquals(3, result.size)
        assertEquals(listOf(103L, 102L, 101L), result.map { it.epochDay })
        assertEquals(60_000, result.first().topWeightGrams)
        assertEquals(5, result.first().topReps)
        assertEquals(1, result.first().sets)
    }

    @Test
    fun performanceSummaryReadsLikeASentence() {
        val performance = ExercisePerformance(
            sessionId = "s1",
            epochDay = java.time.LocalDate.of(2026, 11, 8).toEpochDay(),
            topWeightGrams = 60_000,
            topReps = 8,
            sets = 3,
            durationMs = null,
            distanceMm = null,
        )
        assertEquals("11月8日 · 60公斤 × 8 · 3 组", performanceSummary(performance, WeightUnit.KG))
        assertTrue(performanceSummary(performance, WeightUnit.LB).contains("132.28磅"))
    }

    @Test
    fun durationPerformanceDoesNotRepeatSetCount() {
        val performance = ExercisePerformance(
            sessionId = "s1",
            epochDay = java.time.LocalDate.of(2026, 11, 8).toEpochDay(),
            topWeightGrams = null,
            topReps = null,
            sets = 2,
            durationMs = 90_000,
            distanceMm = null,
        )
        assertEquals("11月8日 · 90 秒 · 2 组", performanceSummary(performance, WeightUnit.KG))
    }

    @Test
    fun musclesAndEquipmentFollowTrainingOrder() {
        assertEquals(
            listOf("胸", "背", "肩", "腿", "核心"),
            orderedMuscles(listOf("核心", "腿", "肩", "背", "胸", "胸")),
        )
        assertEquals(
            listOf("自重", "哑铃", "杠铃", "绳索", "其他"),
            orderedEquipment(listOf("绳索", "其他", "杠铃", "哑铃", "自重")),
        )
        assertEquals(listOf("负重次数", "时长", "距离时长"), orderedTypes(listOf("距离时长", "时长", "负重次数")))
    }

    private fun set(
        session: String,
        day: Long,
        weight: Int,
        reps: Int,
        kind: SetKind = SetKind.NORMAL,
        exerciseId: String = "exercise",
    ) = StatSet(
        sessionId = session,
        epochDay = day,
        exerciseId = exerciseId,
        exerciseName = "杠铃卧推",
        muscleZh = "胸",
        tracking = TrackingType.WEIGHT_REPS,
        kind = kind,
        completed = true,
        reps = reps,
        weightGrams = weight,
        durationMs = null,
        distanceMm = null,
    )
}
