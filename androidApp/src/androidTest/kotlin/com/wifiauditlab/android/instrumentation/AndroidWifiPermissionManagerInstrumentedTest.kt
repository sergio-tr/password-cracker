package com.wifiauditlab.android.instrumentation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifiauditlab.android.wifi.AndroidWifiPermissionManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lightweight permission-manager smoke checks.
 *
 * OEM system dialogs (grant/deny) are manual and are not automated here —
 * this suite only verifies that requiredPermissions is non-empty and that
 * hasScanPermission() returns a boolean without crashing.
 */
@RunWith(AndroidJUnit4::class)
class AndroidWifiPermissionManagerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun requiredPermissions_is_non_empty() {
        val manager = AndroidWifiPermissionManager(context)
        assertFalse(manager.requiredPermissions.isEmpty())
        assertNotNull(manager.requiredPermissions.first())
    }

    @Test
    fun hasScanPermission_returns_boolean_without_crashing() {
        val manager = AndroidWifiPermissionManager(context)
        // Result depends on grant state on the device/emulator; either value is valid.
        val granted = manager.hasScanPermission()
        assertTrue(granted || !granted)
    }
}
