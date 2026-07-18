package com.aryasubramani.vijibackup.app

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InternetPermissionInstrumentedTest {
    @Test
    fun installedAppCanUseTheNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission(
                android.Manifest.permission.INTERNET,
                context.packageName,
            ),
        )
    }
}
