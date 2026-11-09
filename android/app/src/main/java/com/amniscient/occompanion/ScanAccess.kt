// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

/** Camera permission copy. Kept free of Android types so the denied states can be tested on the JVM. */
object ScanAccess {
    const val NEED = "Camera permission is needed to scan. You can also paste the link."

    fun message(granted: Boolean, asked: Boolean, canAskAgain: Boolean): String? = when {
        granted -> null
        !asked -> NEED
        canAskAgain -> Pairing.NO_CAMERA
        else -> Pairing.CAMERA_BLOCKED
    }
}
