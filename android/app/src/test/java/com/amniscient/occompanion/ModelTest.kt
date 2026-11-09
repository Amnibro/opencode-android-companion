// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test
    fun creditIsExact() {
        assertEquals("Originally built by Amniscient", CREDIT)
    }

    @Test
    fun parsesViewWithoutTreatingDemoAsLive() {
        val raw = """
            {"screen":"session","demo":true,"banner":"Local demo fixtures. Not a live OpenCode server.",
            "status":"demo","error":"","credit":"Originally built by Amniscient","unofficial":true,"url":"demo://fixtures",
            "local":false,"running":true,"activeSession":"demo-session-readme","composer":true,"request":null,
            "projects":[],"sessions":[],
            "rows":[{"role":"code","text":"print(\"hello\")","lang":"","name":"","state":0}],
            "permission":{"id":"demo-perm-1","action":"bash","resources":"npm test","message":"wait"},
            "about":["Unofficial prototype for OpenCode","Originally built by Amniscient"]}
        """.trimIndent()
        val state = parseState(raw)
        assertTrue(state.demo)
        assertEquals("demo", state.status)
        assertEquals(CREDIT, state.credit)
        assertEquals(CREDIT, state.about[1])
        assertNull(state.request)
        assertEquals("code", state.rows[0].role)
        assertEquals("demo-perm-1", state.permission?.id)
        assertFalse(raw.contains("Authorization"))
    }
}
