package com.superman.lianji

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 回归测试：Room 的 @Insert(REPLACE) 对父行是「先删再插」，
 * 曾经会把计划里的动作和组级联删掉。这里确认改目标、改休息、改名都不会丢数据。
 */
@RunWith(AndroidJUnit4::class)
class RoutineRepositoryTest {
    private val container
        get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LianjiApp).container

    @Before
    fun reset() {
        runBlocking {
            container.exercises.observe("", "", "", "", false).first { it.isNotEmpty() }
            val dao = container.database.training()
            dao.clearSets()
            dao.clearWorkoutExercises()
            dao.clearSessions()
            dao.clearRoutineSets()
            dao.clearRoutineExercises()
            dao.clearRoutines()
        }
    }

    @Test
    fun editingTargetsKeepsRoutineExercisesAndSets() = runBlocking {
        val routineId = container.training.createRoutine("保留测试")
        container.training.addExercisesToRoutine(routineId, listOf("exercise-squat"), 90)
        val before = container.training.observeRoutine(routineId).first()!!
        val exercise = before.exercises.first().first
        val set = before.exercises.first().second.first()

        container.training.updateRoutineSet(exercise.id, set.id, 12, 80_000)
        container.training.updateRoutineRest(exercise.id, 180)
        container.training.updateRoutine(routineId, "改名后", "")
        container.training.addRoutineSet(exercise.id)

        val after = container.training.observeRoutine(routineId).first()!!
        assertEquals(1, after.exercises.size)
        assertEquals(2, after.exercises.first().second.size)
        assertEquals(12, after.exercises.first().second.first { it.id == set.id }.targetReps)
        assertEquals(180, after.exercises.first().first.restSeconds)
        assertEquals("改名后", after.routine.name)
    }

    @Test
    fun repeatingSessionCopiesExercisesAndResetsCompletion() = runBlocking {
        val sessionId = container.training.ensureSession("原训练")
        container.training.addExerciseToSession(sessionId, "exercise-squat", 90)
        val original = container.training.observeWorkout(sessionId).first()!!
        val set = original.exercises.first().second.first()
        container.training.updateSet(
            com.superman.lianji.core.data.SetEdit(set.id, "normal", 8, 60_000, null, null, null, "状态不错", true),
        )
        container.training.finish(sessionId)

        val repeatedId = container.training.startFromSession(sessionId)!!
        val repeated = container.training.observeWorkout(repeatedId).first()!!
        assertEquals(1, repeated.exercises.size)
        val repeatedSet = repeated.exercises.first().second.first()
        assertEquals(8, repeatedSet.reps)
        assertEquals(60_000, repeatedSet.weightGrams)
        assertEquals(false, repeatedSet.completed)
    }
}
