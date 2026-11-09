// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import kotlin.math.pow

enum class ThemeMode { System, Light, Dark }

enum class PaletteId { Ink, Phosphor, Amber, Cyan }

data class TerminalColors(
    val bg: Long,
    val surface: Long,
    val text: Long,
    val muted: Long,
    val line: Long,
    val accent: Long,
    val code: Long,
    val warn: Long,
)

data class ThemeChoice(val mode: ThemeMode, val palette: PaletteId) {
    fun encode(): String = mode.name.lowercase() + "|" + palette.name.lowercase()

    companion object {
        val Default = ThemeChoice(ThemeMode.System, PaletteId.Ink)

        fun decode(raw: String?): ThemeChoice {
            if (raw.isNullOrBlank()) return Default
            val parts = raw.split("|")
            val mode = when (parts.getOrNull(0)) {
                "light" -> ThemeMode.Light
                "dark" -> ThemeMode.Dark
                else -> ThemeMode.System
            }
            val palette = when (parts.getOrNull(1)) {
                "phosphor" -> PaletteId.Phosphor
                "amber" -> PaletteId.Amber
                "cyan" -> PaletteId.Cyan
                else -> PaletteId.Ink
            }
            return ThemeChoice(mode, palette)
        }
    }
}

fun resolveTheme(choice: ThemeChoice, systemDark: Boolean): TerminalColors {
    val dark = when (choice.mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> systemDark
    }
    return if (dark) darkPalette(choice.palette) else lightPalette(choice.palette)
}

fun darkPalette(id: PaletteId): TerminalColors = when (id) {
    PaletteId.Ink -> TerminalColors(0xFF0B0B0B, 0xFF141414, 0xFFEEEEEE, 0xFFB5B5B5, 0xFF8A8A8A, 0xFFEEEEEE, 0xFF101010, 0xFFE7B2A4)
    PaletteId.Phosphor -> TerminalColors(0xFF0B100C, 0xFF121A14, 0xFFE7F6E4, 0xFFB7D2B2, 0xFF7FA878, 0xFFC6F5B8, 0xFF0E1610, 0xFFE7B2A4)
    PaletteId.Amber -> TerminalColors(0xFF110E0B, 0xFF1A1611, 0xFFF6EBD8, 0xFFD2C0A4, 0xFFA68448, 0xFFE6B15A, 0xFF16130E, 0xFFE7B2A4)
    PaletteId.Cyan -> TerminalColors(0xFF0B0F12, 0xFF12181C, 0xFFE5F3F6, 0xFFB7CDD4, 0xFF6E9AAB, 0xFF9FD7E4, 0xFF0E1418, 0xFFE7B2A4)
}

fun lightPalette(id: PaletteId): TerminalColors = when (id) {
    PaletteId.Ink -> TerminalColors(0xFFF4F4F4, 0xFFE8E8E8, 0xFF1F1F1F, 0xFF3F3F3F, 0xFF5A5A5A, 0xFF1F1F1F, 0xFFEAEAEA, 0xFF8A341F)
    PaletteId.Phosphor -> TerminalColors(0xFFF3F8F2, 0xFFE4F0E2, 0xFF102114, 0xFF2A4630, 0xFF3D6848, 0xFF1E6B32, 0xFFE7F2E5, 0xFF8A341F)
    PaletteId.Amber -> TerminalColors(0xFFF8F3EA, 0xFFF0E6D4, 0xFF24180C, 0xFF4A3820, 0xFF6A5430, 0xFF7A4E00, 0xFFF3EADF, 0xFF8A341F)
    PaletteId.Cyan -> TerminalColors(0xFFF2F7F8, 0xFFE3EEF1, 0xFF0E1C22, 0xFF1E3A44, 0xFF2F5A68, 0xFF0E5F73, 0xFFE7F1F3, 0xFF8A341F)
}

fun contrastRatio(foreground: Long, background: Long): Double {
    val hi = maxOf(luminance(foreground), luminance(background))
    val lo = minOf(luminance(foreground), luminance(background))
    return (hi + 0.05) / (lo + 0.05)
}

private fun luminance(argb: Long): Double {
    val r = channel(argb, 16)
    val g = channel(argb, 8)
    val b = channel(argb, 0)
    return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
}

private fun channel(argb: Long, shift: Int): Double = ((argb shr shift) and 0xFF).toDouble() / 255.0

private fun linearize(c: Double): Double =
    if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

/** Blink only while the activity is resumed and the system has not zeroed animator scale. */
fun cursorBlinks(animatorScale: Float, resumed: Boolean): Boolean = animatorScale > 0f && resumed

/** Status block blinks only for a real connection or a non-demo run. Demo stays steady. */
fun statusBlinks(status: String, running: Boolean, demo: Boolean, animatorScale: Float, resumed: Boolean): Boolean {
    if (!cursorBlinks(animatorScale, resumed) || demo) return false
    return status == "connecting" || status == "reconnecting" || (status == "live" && running)
}

/** Narrow width or enlarged text stacks permission actions so they stay reachable. */
fun stackPermissionActions(widthDp: Float, fontScale: Float): Boolean = widthDp < 400f || fontScale >= 1.15f
