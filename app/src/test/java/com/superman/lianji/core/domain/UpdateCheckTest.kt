package com.superman.lianji.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class UpdateCheckTest {
    @Test
    fun newerVersionCodeIsAvailable() {
        val manifest = UpdateManifest(10300, "1.3.0", "https://page.hopev5.com/lianji/lianji-1.3.0.apk", notes = "更新检查")
        val result = compareUpdate(10200, manifest)
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("1.3.0", (result as UpdateCheckResult.Available).manifest.versionName)
    }

    @Test
    fun sameOrOlderIsUpToDate() {
        val manifest = UpdateManifest(10200, "1.2.0", "https://page.hopev5.com/lianji/lianji-1.2.0.apk")
        assertEquals(UpdateCheckResult.UpToDate, compareUpdate(10200, manifest))
        assertEquals(UpdateCheckResult.UpToDate, compareUpdate(10300, manifest))
    }

    @Test
    fun autoCheckRespectsDailyInterval() {
        val day = TimeUnit.DAYS.toMillis(1)
        assertTrue(shouldAutoCheck(0, 1_000))
        assertFalse(shouldAutoCheck(1_000, 1_000 + day - 1))
        assertTrue(shouldAutoCheck(1_000, 1_000 + day))
    }
}
