// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanAccessTest {
    @Test
    fun permissionCopyMatchesTheDeniedStates() {
        assertNull(ScanAccess.message(granted = true, asked = true, canAskAgain = false))
        assertEquals(ScanAccess.NEED, ScanAccess.message(granted = false, asked = false, canAskAgain = true))
        assertEquals(Pairing.NO_CAMERA, ScanAccess.message(granted = false, asked = true, canAskAgain = true))
        assertEquals(Pairing.CAMERA_BLOCKED, ScanAccess.message(granted = false, asked = true, canAskAgain = false))
    }
}
