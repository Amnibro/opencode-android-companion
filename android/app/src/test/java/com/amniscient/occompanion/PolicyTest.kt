// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PolicyTest {
    @Test
    fun httpsPublic() {
        val o = Policy.classify("https://opencode.example.com", false)
        assertEquals(true, o?.https)
        assertEquals("opencode.example.com", o?.host)
        assertEquals(443, o?.port)
    }

    @Test
    fun httpsExplicitPort() {
        val o = Policy.classify("https://opencode.example.com:49374/extra", false)
        assertEquals(49374, o?.port)
        assertEquals(true, o?.https)
    }

    @Test
    fun httpPublicRefused() {
        assertNull(Policy.classify("http://opencode.example.com", true))
    }

    @Test
    fun httpLoopbackNeedsLocal() {
        assertNull(Policy.classify("http://127.0.0.1:49374", false))
    }

    @Test
    fun httpLoopbackLocal() {
        val o = Policy.classify("http://127.0.0.1:49374", true)
        assertEquals(false, o?.https)
        assertEquals(49374, o?.port)
    }

    @Test
    fun httpEmulatorAndLan() {
        assertEquals(4096, Policy.classify("http://10.0.2.2:4096", true)?.port)
        assertEquals(49374, Policy.classify("http://192.168.1.20:49374", true)?.port)
        assertEquals(49374, Policy.classify("http://100.64.0.8:49374", true)?.port)
        assertNull(Policy.classify("http://100.128.0.1:49374", true))
        assertEquals(80, Policy.classify("http://172.16.4.2", true)?.port)
        assertNull(Policy.classify("http://172.32.0.1", true))
        assertNull(Policy.classify("http://8.8.8.8", true))
    }

    @Test
    fun userinfoAndScheme() {
        assertNull(Policy.classify("https://user:secret@opencode.example.com", false))
        assertNull(Policy.classify("opencode.example.com", false))
    }
}
