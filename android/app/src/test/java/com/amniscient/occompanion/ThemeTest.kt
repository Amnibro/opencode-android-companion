// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun choiceRoundTrips() {
        val choice = ThemeChoice(ThemeMode.Light, PaletteId.Amber)
        assertEquals(choice, ThemeChoice.decode(choice.encode()))
        assertEquals(ThemeChoice.Default, ThemeChoice.decode(null))
        assertEquals(ThemeMode.System, ThemeChoice.decode("nope|nope").mode)
    }

    @Test
    fun everyPaletteClearsContrast() {
        PaletteId.entries.forEach { id ->
            listOf(darkPalette(id), lightPalette(id)).forEach { colors ->
                assertTrue(contrastRatio(colors.text, colors.bg) >= 4.5)
                assertTrue(contrastRatio(colors.muted, colors.bg) >= 4.5)
                assertTrue(contrastRatio(colors.accent, colors.bg) >= 4.5)
                assertTrue(contrastRatio(colors.line, colors.bg) >= 3.0)
                assertTrue(contrastRatio(colors.warn, colors.bg) >= 4.5)
            }
        }
    }

    @Test
    fun systemModeFollowsThePlatform() {
        val choice = ThemeChoice(ThemeMode.System, PaletteId.Ink)
        assertEquals(darkPalette(PaletteId.Ink).bg, resolveTheme(choice, systemDark = true).bg)
        assertEquals(lightPalette(PaletteId.Ink).bg, resolveTheme(choice, systemDark = false).bg)
    }

    @Test
    fun cursorPausesWithoutMotionOrWhenBackgrounded() {
        assertFalse(cursorBlinks(0f, true))
        assertFalse(cursorBlinks(1f, false))
        assertTrue(cursorBlinks(1f, true))
        assertFalse(statusBlinks("live", true, demo = true, 1f, true))
        assertTrue(statusBlinks("live", true, demo = false, 1f, true))
        assertFalse(statusBlinks("idle", false, demo = false, 1f, true))
        assertFalse(statusBlinks("connecting", false, demo = false, 0f, true))
    }

    @Test
    fun permissionActionsStackWhenNarrowOrLargeText() {
        assertTrue(stackPermissionActions(360f, 1f))
        assertTrue(stackPermissionActions(800f, 1.3f))
        assertFalse(stackPermissionActions(800f, 1f))
    }
}
