package com.superman.lianji

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.superman.lianji.core.data.SetEdit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractionTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container: com.superman.lianji.core.data.AppContainer
        get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LianjiApp).container

    @Before
    fun reset() {
        runBlocking {
            // 应用启动时异步写入动作目录，先等它完成，后面的种子数据才找得到动作。
            kotlinx.coroutines.withTimeout(15_000) {
                container.exercises.observe("", "", "", "", false).first { it.isNotEmpty() }
            }
            val dao = container.database.training()
            dao.clearSets()
            dao.clearWorkoutExercises()
            dao.clearSessions()
            dao.clearRoutineSets()
            dao.clearRoutineExercises()
            dao.clearRoutines()
            dao.clearFavorites()
        }
    }

    private fun awaitText(text: String) {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTag(tag: String) {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun longPressSelectsMultipleExercisesAndExits() {
        rule.onNodeWithTag("tab_exercises").performClick()
        awaitText("共 302 个动作")

        rule.onAllNodesWithTag("exercise_row")[0].performTouchInput { longClick() }
        awaitText("已选 1 项")
        rule.onAllNodesWithTag("exercise_row")[1].performClick()
        awaitText("已选 2 项")

        rule.onNodeWithContentDescription("退出多选").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("已选", substring = true).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun batchFavoriteShowsUndo() {
        rule.onNodeWithTag("tab_exercises").performClick()
        awaitText("共 302 个动作")

        rule.onAllNodesWithTag("exercise_row")[0].performTouchInput { longClick() }
        rule.onAllNodesWithTag("exercise_row")[1].performClick()
        awaitText("已选 2 项")

        rule.onNodeWithText("收藏").performClick()
        awaitText("已收藏 2 个动作")
        rule.onNodeWithText("撤销").assertExists()
    }

    @Test
    fun swipeRemovesRoutineExerciseAndUndoRestoresIt() {
        runBlocking {
            val routineId = container.training.createRoutine("测试计划")
            container.training.addExercisesToRoutine(routineId, listOf("exercise-squat"), 90)
            val seeded = container.training.observeRoutine(routineId).first()
            check(seeded?.exercises?.isNotEmpty() == true) { "测试计划没有写入动作" }
        }
        awaitText("测试计划")
        rule.onNodeWithText("测试计划").performClick()
        awaitText("开始计划")
        awaitTag("routine_exercise")

        rule.onAllNodesWithTag("routine_exercise")[0].performTouchInput { swipeLeft() }
        awaitText("已移除")
        rule.onAllNodesWithTag("routine_exercise").assertCountEquals(0)

        rule.onNodeWithText("撤销").performClick()
        awaitTag("routine_exercise")
    }

    @Test
    fun historySwipeDeletesSessionAndUndoRestoresIt() {
        runBlocking {
            val sessionId = container.training.ensureSession("历史测试")
            container.training.addExerciseToSession(sessionId, "exercise-squat", 90)
            container.training.finish(sessionId)
        }
        rule.onNodeWithTag("tab_data").performClick()
        rule.onNodeWithText("历史").performClick()
        awaitTag("session_row")

        rule.onAllNodesWithTag("session_row")[0].performTouchInput { swipeLeft() }
        awaitText("已删除")
        rule.onAllNodesWithTag("session_row").assertCountEquals(0)

        rule.onNodeWithText("撤销").performClick()
        awaitTag("session_row")
    }

    @Test
    fun batchDeleteHistorySessionsAndUndoRestoresThem() {
        runBlocking {
            repeat(2) { index ->
                val id = container.training.ensureSession("批量训练 $index")
                container.training.addExerciseToSession(id, "exercise-squat", 90)
                container.training.finish(id)
            }
        }
        rule.onNodeWithTag("tab_data").performClick()
        rule.onNodeWithText("历史").performClick()
        awaitTag("session_row")

        rule.onAllNodesWithTag("session_row")[0].performTouchInput { longClick() }
        rule.onAllNodesWithTag("session_row")[1].performClick()
        awaitText("已选 2 项")
        rule.onNodeWithText("删除 2 条记录").performClick()
        awaitText("已删除 2 条训练记录")
        rule.onAllNodesWithTag("session_row").assertCountEquals(0)

        rule.onNodeWithText("撤销").performClick()
        awaitTag("session_row")
        rule.onAllNodesWithTag("session_row").assertCountEquals(2)
    }

    @Test
    fun exerciseDetailSwipesBetweenExercises() {
        rule.onNodeWithTag("tab_exercises").performClick()
        awaitText("共 302 个动作")
        rule.onAllNodesWithTag("exercise_row")[0].performClick()
        awaitTag("exercise_pager")
        awaitText("1 / 302")

        rule.onNodeWithTag("exercise_pager").performTouchInput { swipeLeft() }
        awaitText("2 / 302")
    }

    @Test
    fun newSetCopiesPreviousWeightAndReps() {
        val sessionId = runBlocking {
            val id = container.training.ensureSession("自由训练")
            container.training.addExerciseToSession(id, "exercise-squat", 90)
            val details = container.training.observeWorkout(id).first()!!
            val set = details.exercises.first().second.first()
            container.training.updateSet(
                SetEdit(set.id, "normal", reps = 8, weightGrams = 60_000, durationMs = null, distanceMm = null, rpe = null, note = "", completed = false),
            )
            id
        }
        check(sessionId.isNotBlank())

        awaitText("继续")
        rule.onNodeWithText("继续").performClick()
        awaitTag("workout_exercise")
        rule.onNodeWithText("加一组").performClick()
        awaitText("0/2")
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("60").fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun workoutSetCanBeDeletedWithUndo() {
        runBlocking {
            val id = container.training.ensureSession("自由训练")
            container.training.addExerciseToSession(id, "exercise-squat", 90)
            val details = container.training.observeWorkout(id).first()!!
            val set = details.exercises.first().second.first()
            container.training.updateSet(
                SetEdit(set.id, "normal", reps = 8, weightGrams = 60_000, durationMs = null, distanceMm = null, rpe = null, note = "", completed = false),
            )
        }
        awaitText("继续")
        rule.onNodeWithText("继续").performClick()
        awaitTag("workout_exercise")
        rule.onAllNodesWithTag("set_row")[0].performTouchInput { swipeLeft() }
        awaitText("已删除一组")
        rule.onNodeWithText("撤销").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("60").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
