package com.superman.lianji

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.superman.lianji.core.data.SetEdit
import com.superman.lianji.core.domain.RestSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.os.SystemClock
import androidx.compose.ui.test.onAllNodesWithText
import java.io.File
import java.io.FileOutputStream

/**
 * 把主要界面截成 PNG 存到应用外部目录，方便没有图形界面的机器上人工检查。
 * 产物在 /sdcard/Android/data/com.superman.lianji/files/screens 目录下。
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val container
        get() = (context.applicationContext as LianjiApp).container

    private fun shot(name: String) {
        rule.waitForIdle()
        save(name, rule.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun shotNode(name: String, tag: String) {
        rule.waitForIdle()
        save(name, rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap())
    }

    private fun save(name: String, bitmap: Bitmap) {
        val directory = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun awaitTag(tag: String) {
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun back() {
        androidx.test.espresso.Espresso.pressBack()
    }

    private fun awaitText(text: String) {
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Before
    fun seed() {
        runBlocking {
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

            val routineId = container.training.createRoutine("推日")
            container.training.addExercisesToRoutine(
                routineId,
                listOf("exercise-bench-press", "exercise-overhead-press", "exercise-dip"),
                120,
            )
            // 一场已完成的记录，用来看历史页
            val done = container.training.ensureSession("推日")
            container.training.addExerciseToSession(done, "exercise-bench-press", 120)
            val doneSets = container.training.observeWorkout(done).first()!!.exercises.first().second
            container.training.updateSet(SetEdit(doneSets.first().id, "normal", 8, 60_000, null, null, null, "", true))
            container.training.finish(done)
            // 一场正在进行，用来看训练页
            val active = container.training.ensureSession("推日")
            container.training.addExercisesToSession(active, listOf("exercise-bench-press", "exercise-incline-dumbbell-press"), 120)
            val activeSets = container.training.observeWorkout(active).first()!!.exercises
            activeSets.forEachIndexed { index, (_, sets) ->
                container.training.updateSet(SetEdit(sets.first().id, "normal", 8 + index, 60_000 + index * 2_500, null, null, null, "", index == 0))
            }
            container.settings.setRest(
                RestSnapshot(
                    SystemClock.elapsedRealtime() + 90_000,
                    90,
                    System.currentTimeMillis() + 90_000,
                    container.feedback.bootCount(),
                    active,
                    "杠铃卧推",
                ),
            )
        }
        awaitText("继续")
    }

    @Test
    fun captureMainScreens() {
        shot("01-today")

        rule.onNodeWithTag("tab_exercises").performClick()
        awaitText("共 302 个动作")
        shot("02-library")

        rule.onNodeWithContentDescription("筛选").performClick()
        awaitText("筛选动作")
        shotNode("03-filter-sheet", "filter_sheet")
        back()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("筛选动作").fetchSemanticsNodes().isEmpty() }

        rule.onAllNodesWithTag("exercise_row")[0].performTouchInput { longClick() }
        rule.onAllNodesWithTag("exercise_row")[1].performClick()
        awaitText("已选 2 项")
        shot("04-selection")

        rule.onNodeWithContentDescription("退出多选").performClick()
        rule.onAllNodesWithTag("exercise_row")[0].performClick()
        awaitTag("exercise_pager")
        shot("05-exercise-detail")
        rule.onNodeWithContentDescription("返回").performClick()

        rule.onNodeWithTag("tab_today").performClick()
        awaitText("继续")
        rule.onNodeWithText("继续").performClick()
        awaitTag("workout_exercise")
        shot("06-workout")
        rule.onAllNodesWithText("1")[0].performClick()
        awaitText("编辑这一组")
        shotNode("07-set-editor", "set_editor_sheet")
        back()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("编辑这一组").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("返回").performClick()

        rule.onNodeWithTag("tab_today").performClick()
        awaitText("点击查看或调整目标")
        rule.onNodeWithText("点击查看或调整目标").performClick()
        awaitTag("routine_exercise")
        shot("08-routine")
        rule.onNodeWithContentDescription("返回").performClick()

        rule.onNodeWithTag("tab_data").performClick()
        rule.onNodeWithText("历史").performClick()
        awaitTag("session_row")
        shot("09-history")
        rule.onNodeWithTag("tab_data").performClick()
        rule.onNodeWithText("概览").performClick()
        shot("10-data")
        rule.onNodeWithTag("tab_profile").performClick()
        shot("11-profile")
        rule.onNodeWithTag("tab_today").performClick()
        rule.onNodeWithTag("start_fab").performClick()
        awaitTag("start_sheet")
        shotNode("12-start-sheet", "start_sheet")
        back()
    }
}
