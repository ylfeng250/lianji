package com.superman.lianji

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 发布包开了资源压缩，动作图又只按名字在运行时查找（getIdentifier）。
 * 这个测试在真机/模拟器上确认每一帧都能被解析到，回归时会直接失败。
 */
@RunWith(AndroidJUnit4::class)
class ExerciseArtResourceTest {
    @Test
    fun everyCatalogFrameResolvesToDrawable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val payload = JSONObject(
            context.assets.open("catalog/exercises_zh.json").bufferedReader().use { it.readText() },
        )
        val exercises = payload.getJSONArray("exercises")
        var frames = 0
        repeat(exercises.length()) { index ->
            val item = exercises.getJSONObject(index)
            val names = item.getJSONArray("frames")
            repeat(names.length()) { frameIndex ->
                val name = names.getString(frameIndex)
                val id = context.resources.getIdentifier(name, "drawable", context.packageName)
                assertTrue("${item.getString("nameZh")} 的第 ${frameIndex + 1} 帧 $name 没有打包进安装包", id != 0)
                frames++
            }
        }
        assertEquals("动作帧数量异常", 906, frames)
    }
}
