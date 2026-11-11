package com.superman.lianji.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RestAndBackupTest {
    @Test
    fun restUsesMonotonicClockUntilReboot() {
        val snapshot = RestSnapshot(10_000, 90, 2_000_000, 4, "session", "杠铃卧推")
        assertEquals(4_000L, remainingMillis(snapshot, 6_000, 1_900_000, 4))
        assertEquals(50_000L, remainingMillis(snapshot, 100, 1_950_000, 5))
        assertEquals(0L, remainingMillis(snapshot, 20_000, 3_000_000, 4))
    }

    @Test
    fun backupRoundTripAndChecksumGuard() {
        val bundle = BackupBundle(
            manifest = BackupManifest(1, "1.0.0", 1, 1_700_000_000_000),
            userData = UserDataBackup(
                sessions = listOf(
                    SessionBackup("s", "自由训练", "", null, "completed", 1_700_000_000_000, 1_700_000_100_000, 20_000, "Asia/Shanghai"),
                ),
            ),
            settings = SettingsBackup("KG", 90, true, true, "SYSTEM", true),
        )
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, bundle) }.toByteArray()
        val restored = BackupArchive.read(ByteArrayInputStream(bytes))
        assertEquals("自由训练", restored.userData.sessions.single().title)
        val broken = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(broken).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zip.write("""{"formatVersion":1,"appVersion":"1.0.0","catalogRevision":1,"exportedAtEpochMs":1}""".toByteArray())
            zip.putNextEntry(java.util.zip.ZipEntry("user_data.json"))
            zip.write("{}".toByteArray())
            zip.putNextEntry(java.util.zip.ZipEntry("settings.json"))
            zip.write("{}".toByteArray())
            zip.putNextEntry(java.util.zip.ZipEntry("checksums.json"))
            zip.write("""{"manifest":"00","userData":"00","settings":"00"}""".toByteArray())
        }
        assertThrows(BackupException::class.java) { BackupArchive.read(ByteArrayInputStream(broken.toByteArray())) }
    }
}
