package com.superman.lianji.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomExerciseTypeTest {
    @Test
    fun normalizeFallsBackToDuration() {
        assertEquals("duration", normalizeCustomExerciseType("duration"))
        assertEquals("distance_duration", normalizeCustomExerciseType("distance_duration"))
        assertEquals("duration", normalizeCustomExerciseType("unknown"))
    }

    @Test
    fun typeZhMatchesCatalogLabels() {
        assertEquals("时长", customExerciseTypeZh("duration"))
        assertEquals("距离与时长", customExerciseTypeZh("distance_duration"))
        assertEquals("自重次数", customExerciseTypeZh("bodyweight_reps"))
        assertEquals("负重次数", customExerciseTypeZh("weight_reps"))
    }
}
