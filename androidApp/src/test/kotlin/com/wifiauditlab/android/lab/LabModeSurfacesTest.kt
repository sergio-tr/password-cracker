package com.wifiauditlab.android.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabModeSurfacesTest {
    @Test
    fun labModeOff_hidesEveryLabSurface() {
        assertFalse(LabModeSurfaces.showLabTab(false))
        assertFalse(LabModeSurfaces.showLabEntryCtas(false))
        assertFalse(LabModeSurfaces.showLabRegistryActions(false))
        assertFalse(LabModeSurfaces.showLabSettingsExtras(false))
        assertFalse(LabModeSurfaces.showLabVerificationOptions(false))
    }

    @Test
    fun labModeOn_showsEveryLabSurface() {
        assertTrue(LabModeSurfaces.showLabTab(true))
        assertTrue(LabModeSurfaces.showLabEntryCtas(true))
        assertTrue(LabModeSurfaces.showLabRegistryActions(true))
        assertTrue(LabModeSurfaces.showLabSettingsExtras(true))
        assertTrue(LabModeSurfaces.showLabVerificationOptions(true))
    }

    @Test
    fun safeRouteAfterLabHidden_isNearby() {
        assertEquals("nearby", LabModeSurfaces.SAFE_TOP_LEVEL_AFTER_LAB_HIDDEN)
    }
}
