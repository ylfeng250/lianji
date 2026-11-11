package com.superman.lianji

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflinePermissionTest {
    @Test
    fun releasePackageDoesNotRequestNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions?.toList().orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(permissions.contains("android.permission.VIBRATE"))
    }
}
