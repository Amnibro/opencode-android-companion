// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * OpenCode's app (anomalyco/opencode PR 44876) sets Inter for UI text and
 * IBM Plex Mono for code. Both are SIL Open Font License 1.1. The opencode.ai
 * site uses OpenTUI Mono for badges and mono text; this screen follows the app
 * pair so prose and code are not the same face.
 */
val UiFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val CodeFont = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

const val FONT_NOTICE =
    "Text is Inter (SIL OFL 1.1, Copyright 2016 The Inter Project Authors). Code is IBM Plex Mono (SIL OFL 1.1, Copyright 2017 IBM Corp., reserved font name Plex). OpenCode's app uses this pair. Licenses are in third_party/fonts."
