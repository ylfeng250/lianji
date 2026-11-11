package com.superman.lianji.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogValidator {
    @Test
    fun catalogIsCompleteChineseAndOffline() {
        val root = File("..").canonicalFile
        val catalog = File(root, "app/src/main/assets/catalog/exercises_zh.json").readText()
        assertFalse(catalog.contains(".jpg"))
        assertFalse(catalog.contains(".png"))
        assertFalse(catalog.contains("http://"))
        assertFalse(catalog.contains("https://"))
        val slugs = Regex("\"slug\"\\s*:\\s*\"([^\"]+)\"").findAll(catalog).map { it.groupValues[1] }.toList()
        assertEquals(302, slugs.distinct().size)
        val frames = Regex("\"ex_[a-z0-9_]+\"").findAll(catalog).map { it.value.trim('"') }.toList()
        assertEquals(906, frames.size)
        frames.forEach { name ->
            assertTrue(File(root, "app/src/main/res/drawable/$name.xml").isFile)
        }
        val names = Regex("\"nameZh\"\\s*:\\s*\"([^\"]+)\"").findAll(catalog).map { it.groupValues[1] }.toList()
        assertEquals(302, names.size)
        names.forEach { assertFalse(it.contains(Regex("[A-Za-z]"))) }
    }
}
