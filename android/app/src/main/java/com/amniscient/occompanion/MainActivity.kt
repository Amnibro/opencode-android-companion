// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("companion-theme", Context.MODE_PRIVATE)
        val fromIntent = intentTheme(intent?.getStringExtra("theme"), intent?.getStringExtra("palette"))
        if (fromIntent != null) prefs.edit().putString("choice", fromIntent.encode()).apply()
        val start = intent?.getStringExtra("start")
        setContent {
            val engine: Engine = viewModel()
            val context = LocalContext.current
            var choice by remember {
                mutableStateOf(ThemeChoice.decode(prefs.getString("choice", null)))
            }
            val dark = isSystemInDarkTheme()
            val colors = resolveTheme(choice, dark)
            val owner = LocalLifecycleOwner.current
            val life by owner.lifecycle.currentStateFlow.collectAsState()
            val resumed = life.isAtLeast(Lifecycle.State.RESUMED)
            val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val motion = TermMotion(cursorBlinks(scale, resumed), scale, resumed)
            CompositionLocalProvider(LocalTerm provides colors, LocalMotion provides motion) {
                Box(Modifier.fillMaxSize().background(Color(colors.bg.toInt()))) {
                    CompanionApp(engine, start, choice, intent?.getStringExtra("pair_image")) {
                        choice = it
                        prefs.edit().putString("choice", it.encode()).apply()
                    }
                }
            }
        }
    }
}

private fun intentTheme(mode: String?, palette: String?): ThemeChoice? {
    if (mode.isNullOrBlank() && palette.isNullOrBlank()) return null
    val base = ThemeChoice.Default
    return ThemeChoice.decode(
        (mode ?: base.mode.name.lowercase()) + "|" + (palette ?: base.palette.name.lowercase()),
    )
}

data class TermMotion(val blink: Boolean, val animatorScale: Float, val resumed: Boolean)
