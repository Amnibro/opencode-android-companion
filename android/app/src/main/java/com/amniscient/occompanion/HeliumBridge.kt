// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

object HeliumBridge {
    init {
        System.loadLibrary("companion")
    }

    @JvmStatic
    external fun handleNative(event: String): String

    fun handle(event: String): String = handleNative(event)
}
