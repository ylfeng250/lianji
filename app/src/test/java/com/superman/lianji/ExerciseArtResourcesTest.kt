package com.superman.lianji

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 动作图只按名字在运行时查找，静态资源和代码之间没有直接引用。
 * 这里守住两端：目录里每张图都能找到，且每张图都被资源保留规则覆盖，
 * 否则 R8 资源压缩会把它们删掉，详情页就只剩空白框、没有三帧动画。
 */
class ExerciseArtResourcesTest {
    private val module: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "src/main/assets/catalog/exercises_zh.json").isFile }
        ?: error("找不到 app 模块目录")

    private val catalogFrames: List<String> by lazy {
        val payload = Json.parseToJsonElement(
            File(module, "src/main/assets/catalog/exercises_zh.json").readText(),
        ).jsonObject
        payload.getValue("exercises").jsonArray
            .flatMap { item -> item.jsonObject.getValue("frames").jsonArray.map { it.jsonPrimitive.content } }
    }

    @Test
    fun everyCatalogFrameHasVectorDrawable() {
        val drawables = File(module, "src/main/res/drawable")
        val missing = catalogFrames.filterNot { name -> File(drawables, "$name.xml").isFile }
        assertEquals("目录里有动作图缺少对应的 VectorDrawable", emptyList<String>(), missing)
        assertTrue("动作数量异常，只有 ${catalogFrames.size} 帧", catalogFrames.size >= 900)
    }

    @Test
    fun everyFrameIsCoveredByResourceKeepRule() {
        assertTrue("动作图应该沿用 ex_ 前缀以便被 res/raw/keep.xml 保留", catalogFrames.all { it.startsWith("ex_") })
        val keep = File(module, "src/main/res/raw/keep.xml").readText()
        assertTrue("res/raw/keep.xml 必须保留 @drawable/ex_*", keep.contains("@drawable/ex_*"))
    }

    @Test
    fun noUnusedExerciseDrawablesAreLeftBehind() {
        val catalogNames = catalogFrames.toSet()
        val onDisk = File(module, "src/main/res/drawable").listFiles()
            .orEmpty()
            .map { it.name }
            .filter { it.startsWith("ex_") && it.endsWith(".xml") }
            .map { it.removeSuffix(".xml") }
        assertEquals("drawable 目录里有目录未引用的动作图", emptyList<String>(), onDisk.filterNot { it in catalogNames })
    }
}
