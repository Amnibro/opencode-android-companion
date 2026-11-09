// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionTest {
    @Test
    fun hashMatchesGrokRemote() {
        assertEquals("9dbc787", rxHash("hello from the fixture"))
        assertEquals("811c9dc5", rxHash(""))
    }

    @Test
    fun toggleStoresAndClearsLocally() {
        val book = ReactionBook()
        book.toggle("ses", "hello from the fixture", "👍")
        assertEquals("👍", book.chip("ses", "hello from the fixture"))
        book.toggle("ses", "hello from the fixture", "👍")
        assertNull(book.chip("ses", "hello from the fixture"))
    }

    @Test
    fun pendingNoteIsLocalAndAcks() {
        val book = ReactionBook()
        book.toggle("ses", "a reply", "😂")
        val note = book.pendingNote("ses")
        assertTrue(note.startsWith("[User reactions]"))
        assertTrue(note.contains("😂"))
        book.ack("ses")
        assertEquals("", book.pendingNote("ses"))
        assertEquals("😂", book.chip("ses", "a reply"))
    }

    @Test
    fun manualLockIgnoresAgentTemperature() {
        assertEquals(0.65f, nextTemperature(0.65f, modeManual = true, fromAgent = true, proposed = 0.1f))
        assertEquals(0.2f, nextTemperature(0.65f, modeManual = false, fromAgent = true, proposed = 0.2f))
        assertEquals(1f, nextTemperature(0.4f, modeManual = true, fromAgent = false, proposed = 2f))
    }

    @Test
    fun agentStampIsStrippedFromVisibleText() {
        val marks = parseAgentMarks("done\n[[rx:👍]]\n[[rx-temp:0.40]]")
        assertEquals("done", marks.text)
        assertEquals("👍", marks.stamp)
        assertEquals(0.40f, marks.temperature)
    }
}
