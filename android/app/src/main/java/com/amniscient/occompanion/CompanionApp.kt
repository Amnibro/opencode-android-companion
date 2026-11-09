// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.amniscient.occompanion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalTerm = staticCompositionLocalOf { darkPalette(PaletteId.Ink) }
val LocalMotion = staticCompositionLocalOf { TermMotion(blink = false, animatorScale = 1f, resumed = true) }

private val Gutter = 16.dp
private val Stack = 12.dp
private val Control = 48.dp

internal fun TerminalColors.ink() = Color(text.toInt())
internal fun TerminalColors.dim() = Color(muted.toInt())
private fun TerminalColors.paper() = Color(bg.toInt())
private fun TerminalColors.panel() = Color(surface.toInt())
private fun TerminalColors.rule() = Color(line.toInt())
private fun TerminalColors.mark() = Color(accent.toInt())
private fun TerminalColors.well() = Color(code.toInt())
internal fun TerminalColors.warnc() = Color(warn.toInt())

/** 1dp edge. Dark and light both use the theme line, softened so the control reads as bounded rather than boxed. */
internal fun TerminalColors.hair(): Color {
    val dark = ((bg shr 16) and 0xFF) < 80
    return Color(line.toInt()).copy(alpha = if (dark) 0.5f else 0.4f)
}

internal fun TerminalColors.bubble(): Color {
    fun channel(value: Long, shift: Int) = ((value shr shift) and 0xFF).toInt()
    val darkPage = channel(bg, 16) < 80
    val amount = if (darkPage) 0.22f else 0.08f
    fun mix(shift: Int): Int {
        val from = channel(surface, shift)
        val to = channel(text, shift)
        return (from + ((to - from) * amount)).toInt().coerceIn(0, 255)
    }
    return Color(mix(16), mix(8), mix(0))
}

@Composable
fun CompanionApp(engine: Engine, start: String?, choice: ThemeChoice, injectImage: String? = null, onTheme: (ThemeChoice) -> Unit) {
    LaunchedEffect(start) { engine.boot(start) }
    val model by engine.state.collectAsState()
    val term = LocalTerm.current
    Column(
        Modifier
            .fillMaxSize()
            .background(term.paper())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Gutter),
    ) {
        if (model.screen == "connect" || model.screen == "projects") {
            QuietHeader(if (model.screen == "projects") "Sessions" else "Connect") { engine.screen("about") }
        }
        if (model.error.isNotBlank()) ErrorLine(model.error) { engine.dismiss() }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            if (wide && model.screen == "session") {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(260.dp).fillMaxHeight().background(term.panel()).padding(vertical = Stack)) {
                        ProjectList(model, engine, compact = true)
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(term.rule()))
                    SessionPane(model, engine, Modifier.weight(1f).padding(start = Gutter))
                }
            } else {
                when (model.screen) {
                    "projects" -> ProjectList(model, engine, compact = false)
                    "session" -> SessionPane(model, engine, Modifier.fillMaxSize())
                    "about" -> AboutPane(model, engine, choice, onTheme)
                    else -> ConnectPane(model, engine, choice, onTheme, start, injectImage)
                }
            }
        }
    }
}

@Composable
private fun QuietHeader(title: String, onAbout: () -> Unit) {
    val term = LocalTerm.current
    Row(
        Modifier.fillMaxWidth().height(56.dp).border(1.dp, term.hair(), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = term.ink(), fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, modifier = Modifier.weight(1f).semantics { heading() })
        Text(
            "About",
            color = term.ink(),
            fontFamily = UiFont,
            fontSize = 15.sp,
            modifier = Modifier.border(1.dp, term.hair(), RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onAbout).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TopBar(model: CompanionState, engine: Engine) {
    val term = LocalTerm.current
        Row(
            Modifier.fillMaxWidth().height(Control).background(term.paper()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mark()
        Spacer(Modifier.width(Stack))
        Column(Modifier.weight(1f)) {
            Text("Companion", color = term.ink(), fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.semantics { heading() })
            Text("Unofficial prototype for OpenCode", color = term.dim(), fontFamily = UiFont, fontSize = 12.sp)
        }
        StatusMark(model)
        if (model.screen != "about") {
            Spacer(Modifier.width(Stack))
            TermButton("About", { engine.screen("about") })
        }
    }
}

@Composable
private fun StatusMark(model: CompanionState) {
    val term = LocalTerm.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        BlockCursor(term.mark(), false, "status " + model.status)
        Spacer(Modifier.width(6.dp))
        Text(model.status.ifBlank { "idle" }, color = term.dim(), fontFamily = CodeFont, fontSize = 12.sp)
    }
}

@Composable
private fun Mark() {
    val term = LocalTerm.current
    Canvas(Modifier.size(18.dp).semantics { contentDescription = "Companion mark, not the OpenCode logo" }) {
        val w = size.width
        val h = size.height
        drawRect(term.ink(), Offset(w * 0.08f, h * 0.18f), Size(w * 0.18f, h * 0.64f))
        drawRect(term.mark(), Offset(w * 0.72f, h * 0.18f), Size(w * 0.18f, h * 0.64f))
    }
}

@Composable
private fun Banner(text: String) {
    val term = LocalTerm.current
    Text(text, color = term.dim(), fontFamily = UiFont, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp))
}

@Composable
private fun ErrorLine(text: String, onDismiss: () -> Unit) {
    val term = LocalTerm.current
    Row(Modifier.fillMaxWidth().padding(bottom = Stack), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = term.warnc(), fontFamily = UiFont, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TermButton("Dismiss", onDismiss)
    }
}

@Composable
private fun ConnectPane(
    model: CompanionState,
    engine: Engine,
    choice: ThemeChoice,
    onTheme: (ThemeChoice) -> Unit,
    start: String?,
    injectImage: String?,
) {
    val term = LocalTerm.current
    var page by remember { mutableStateOf(if (start == "scan") "scan" else "home") }
    var url by remember { mutableStateOf("https://") }
    var local by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var address by remember { mutableStateOf(engine.lastHost()) }
    if (page == "scan") {
        ScanPane(injectImage, model.error, onBack = { page = "home" }) { code -> engine.pair(code, address) }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Scan the code from opencode pair on your computer. Your sessions open here.",
            color = term.ink(),
            fontFamily = UiFont,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        PillButton("Scan QR code", primary = true) { page = "scan" }
        Spacer(Modifier.height(10.dp))
        TermField(pasted, { pasted = it }, "Paste from opencode pair", KeyboardType.Text, singleLine = false)
        Spacer(Modifier.height(8.dp))
        TermField(address, { address = it }, "Computer address", KeyboardType.Uri)
        Spacer(Modifier.height(8.dp))
        PillButton("Connect", primary = pasted.isNotBlank()) {
            engine.pair(pasted, address)
        }
        if (model.error.isNotBlank() && page == "home") {
            Text(model.error, color = term.warnc(), fontFamily = UiFont, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        PillButton("Sign in", enabled = false) {}
        Text("Coming soon", color = term.dim(), fontFamily = CodeFont, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 8.dp))
        Text(
            if (page == "manual") "Hide manual entry" else "Enter manually",
            color = term.ink(),
            fontFamily = UiFont,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp).clickable(role = Role.Button) {
                page = if (page == "manual") "home" else "manual"
            },
        )
        if (page == "manual") {
            TermField(url, { url = it }, "Server URL", KeyboardType.Uri)
            Spacer(Modifier.height(8.dp))
            TermToggle("Local HTTP", local) { local = it }
            TermField(password, { password = it }, "Password or pairing token", KeyboardType.Password, password = true)
            Spacer(Modifier.height(8.dp))
            PillButton("Connect with password", primary = password.isNotBlank()) {
                val chars = password.toCharArray()
                password = ""
                engine.connect(url.ifBlank { address }, local, chars)
            }
            Text(
                "Clear stored password",
                color = term.dim(),
                fontFamily = UiFont,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp).clickable(role = Role.Button) { engine.clearSecret() },
            )
        }
    }
}

@Composable
internal fun PillButton(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val term = LocalTerm.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, term.hair(), RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(when {
                !enabled -> term.bubble()
                primary -> if (((term.bg shr 16) and 0xFF) < 80) Color(0xFFFFFFFF.toInt()) else Color(0xFF111111.toInt())
                else -> term.bubble()
            })
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = if (enabled) label else "$label, coming soon" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = when {
                !enabled -> term.dim()
                primary -> if (((term.bg shr 16) and 0xFF) < 80) Color(0xFF111111.toInt()) else Color(0xFFF7F7F7.toInt())
                else -> term.ink()
            },
            fontFamily = UiFont,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun ThemePicker(choice: ThemeChoice, onTheme: (ThemeChoice) -> Unit) {
    val term = LocalTerm.current
    Column(Modifier.fillMaxWidth().padding(top = Stack), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Theme", color = term.dim(), fontFamily = CodeFont, fontSize = 12.sp)
        WordRow(ThemeMode.entries.map { it.name.lowercase() to (choice.mode == it) }) { label ->
            onTheme(choice.copy(mode = ThemeMode.entries.first { it.name.equals(label, true) }))
        }
        WordRow(PaletteId.entries.map { it.name.lowercase() to (choice.palette == it) }) { label ->
            onTheme(choice.copy(palette = PaletteId.entries.first { it.name.equals(label, true) }))
        }
    }
}

@Composable
private fun WordRow(items: List<Pair<String, Boolean>>, onPick: (String) -> Unit) {
    val term = LocalTerm.current
    Row(Modifier.fillMaxWidth().height(Control), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gutter)) {
        items.forEach { (label, on) ->
            Text(
                label,
                color = if (on) term.mark() else term.dim(),
                fontFamily = UiFont,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 15.sp,
                modifier = Modifier.clickable(role = Role.Button) { onPick(label) }.semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun ProjectList(model: CompanionState, engine: Engine, compact: Boolean) {
    val term = LocalTerm.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Stack)) {
        if (model.sessions.isEmpty()) Text("No sessions yet", color = term.dim(), fontFamily = UiFont, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
        model.sessions.forEach { item ->
            SessionRow(item, projectName(model, item)) { engine.openSession(item.id) }
        }
        Spacer(Modifier.height(SectionGap))
        Text("Projects", color = term.dim(), fontFamily = CodeFont, fontSize = 12.sp)
        if (model.projects.isEmpty()) Text("None", color = term.dim(), fontFamily = UiFont, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        model.projects.forEach { item ->
            Text(item.title.ifBlank { "Untitled" }, color = term.ink(), fontFamily = UiFont, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            if (item.id.startsWith("/") && !item.id.startsWith("/demo")) {
                Text(item.id, color = term.dim(), fontFamily = UiFont, fontSize = 13.sp)
            }
        }
        if (!compact) {
            Spacer(Modifier.height(SectionGap))
            Text(
                "Connection",
                color = term.ink(),
                fontFamily = UiFont,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp).clickable(role = Role.Button) { engine.screen("connect") },
            )
        }
    }
}

private val SectionGap = 20.dp

@Composable
private fun SessionPane(model: CompanionState, engine: Engine, modifier: Modifier) {
    val term = LocalTerm.current
    val rx by engine.reactionMap.collectAsState()
    var draft by remember { mutableStateOf("") }
    var attachNote by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val title = model.sessions.firstOrNull { it.id == model.activeSession }?.title?.ifBlank { null } ?: "Session"
    Column(modifier.fillMaxHeight().fillMaxWidth().background(term.paper())) {
        Row(
            Modifier.fillMaxWidth().height(Control).border(1.dp, term.hair(), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sessions",
                color = term.ink(),
                fontFamily = UiFont,
                fontSize = 15.sp,
                modifier = Modifier.border(1.dp, term.hair(), RoundedCornerShape(10.dp)).clickable(role = Role.Button) { engine.screen("projects") }.padding(horizontal = 8.dp, vertical = 4.dp).semantics { contentDescription = "Sessions" },
            )
            Text(
                title,
                color = term.ink(),
                fontFamily = UiFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(
                "About",
                color = term.ink(),
                fontFamily = UiFont,
                fontSize = 15.sp,
                modifier = Modifier.border(1.dp, term.hair(), RoundedCornerShape(10.dp)).clickable(role = Role.Button) { engine.screen("about") }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (model.banner.isNotBlank()) Banner(model.banner)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().clipToBounds().verticalScroll(scroll).padding(bottom = 8.dp)) {
                if (model.rows.isEmpty()) {
                    Text("No messages", color = term.dim(), fontFamily = UiFont, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                } else {
                    QuietLine("Today")
                }
                model.rows.forEachIndexed { index, row ->
                    val previous = model.rows.getOrNull(index - 1)?.role
                    Spacer(Modifier.height(if (index == 0 || previous != row.role) Gutter else 8.dp))
                    TranscriptRowView(row, rx[model.activeSession]?.get(rxHash(row.text))?.emoji) { emoji ->
                        engine.react(row.text, emoji)
                    }
                }
                if (model.running) {
                    Spacer(Modifier.height(8.dp))
                    WorkingLine()
                }
            }
            if (scroll.maxValue > 48 && scroll.value < scroll.maxValue - 48) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(term.bubble())
                        .clickable(role = Role.Button) { scope.launch { scroll.animateScrollTo(scroll.maxValue) } }
                        .semantics { contentDescription = "Scroll to latest" },
                    contentAlignment = Alignment.Center,
                ) {
                    ChevronDown(term.ink())
                }
            }
        }
        model.permission?.let { ask -> PermissionCard(ask, engine) }
        if (attachNote) {
            Text(
                "Nothing to attach in this prototype.",
                color = term.dim(),
                fontFamily = UiFont,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        ComposerBar(
            draft = draft,
            onDraft = { draft = it },
            running = model.running,
            onSend = {
                val text = draft
                draft = ""
                focus.clearFocus()
                if (text.isNotBlank()) engine.send(text)
            },
            onStop = { engine.interrupt() },
            onAdd = { attachNote = !attachNote },
        )
    }
}

@Composable
private fun QuietLine(text: String) {
    val term = LocalTerm.current
    Text(
        text,
        color = term.dim(),
        fontFamily = CodeFont,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun WorkingLine() {
    val term = LocalTerm.current
    val motion = LocalMotion.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        BlockCursor(term.ink(), motion.blink, "streaming cursor")
        Text("Working", color = term.dim(), fontFamily = CodeFont, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun ChevronDown(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.15f, h * 0.3f), Offset(w * 0.5f, h * 0.72f), strokeWidth = 2.dp.toPx())
        drawLine(color, Offset(w * 0.85f, h * 0.3f), Offset(w * 0.5f, h * 0.72f), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun TranscriptRowView(row: TranscriptRow, chip: String?, onReact: (String) -> Unit) {
    val term = LocalTerm.current
    val shown = if (row.role == "assistant" || row.role == "user" || row.role == "code") parseAgentMarks(row.text).text else row.text
    var open by remember(row.text, row.role) { mutableStateOf(false) }
    val reactable = row.role == "user" || row.role == "assistant"
    val user = row.role == "user"
    val centered = row.role == "reasoning" || row.role == "tool" || row.role == "status"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        when (row.role) {
            "user" -> BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    shown.trim(),
                    color = term.ink(),
                    fontFamily = UiFont,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .widthIn(max = maxWidth * 0.78f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(term.bubble())
                        .combinedClickable(onClick = {}, onLongClick = { open = !open })
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            "assistant" -> Text(
                shown.trim(),
                color = term.ink(),
                fontFamily = UiFont,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { open = !open }),
            )
            "reasoning" -> DisclosureLine(if (open) "Hide thinking" else "Thinking", open, shown.trim()) { open = !open }
            "code" -> CodeBlock(shown, row.lang)
            "tool" -> DisclosureLine(
                (row.name.ifBlank { "tool" }) + " · " + toolState(row.state),
                open,
                row.text.trim(),
            ) { open = !open }
            "error" -> Text(
                shown.trim(),
                color = term.warnc(),
                fontFamily = UiFont,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(term.bubble()).padding(horizontal = 14.dp, vertical = 10.dp),
            )
            else -> Text(
                shown.trim(),
                color = term.dim(),
                fontFamily = CodeFont,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            )
        }
        if (!chip.isNullOrBlank()) {
            Text(
                chip,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(term.bubble()).padding(horizontal = 8.dp, vertical = 2.dp).semantics {
                    contentDescription = "reaction $chip"
                },
            )
        }
        if (reactable && open) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RX_SET.forEach { em ->
                    Text(
                        em,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (chip == em) term.ink() else term.bubble())
                            .clickable(role = Role.Button) { onReact(em); open = false }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .semantics { contentDescription = if (chip == em) "Clear $em" else "React $em" },
                    )
                }
            }
        }
    }
}

@Composable
private fun DisclosureLine(label: String, open: Boolean, detail: String, onToggle: () -> Unit) {
    val term = LocalTerm.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = term.dim(),
            fontFamily = CodeFont,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(role = Role.Button, onClick = onToggle).padding(vertical = 2.dp),
        )
        if (open && detail.isNotBlank()) {
            Text(detail, color = term.dim(), fontFamily = UiFont, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 24.dp, end = 24.dp))
        }
    }
}

private fun toolState(state: Int) = when (state) {
    2 -> "completed"
    3 -> "failed"
    else -> "running"
}

@Composable
private fun CodeBlock(text: String, lang: String) {
    val term = LocalTerm.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(term.well()).padding(12.dp)) {
        if (lang.isNotBlank()) Text(lang, color = term.dim(), fontFamily = CodeFont, fontSize = 12.sp)
        SelectionContainer {
            Text(
                highlight(text.trimEnd(), term),
                fontFamily = CodeFont,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics {
                    contentDescription = "Code block" + if (lang.isBlank()) "" else " $lang"
                },
            )
        }
    }
}

private fun highlight(text: String, term: TerminalColors) = buildAnnotatedString {
    val keywords = setOf("fn", "fun", "let", "var", "return", "if", "else", "struct", "import", "class", "const", "true", "false")
    var i = 0
    while (i < text.length) {
        if (text.startsWith("//", i) || (text[i] == '#' && (i == 0 || text[i - 1] == '\n'))) {
            val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
            pushStyle(SpanStyle(color = Color(term.muted), fontFamily = CodeFont))
            append(text.substring(i, end))
            pop()
            i = end
        } else if (text[i] == '"') {
            var j = i + 1
            while (j < text.length && text[j] != '"') {
                if (text[j] == '\\') j++
                j++
            }
            if (j < text.length) j++
            pushStyle(SpanStyle(color = Color(term.accent), fontFamily = CodeFont))
            append(text.substring(i, j.coerceAtMost(text.length)))
            pop()
            i = j
        } else if (text[i].isLetter()) {
            var j = i + 1
            while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
            val word = text.substring(i, j)
            pushStyle(SpanStyle(color = Color(if (word in keywords) term.text else term.muted), fontFamily = CodeFont, fontWeight = if (word in keywords) FontWeight.Medium else FontWeight.Normal))
            append(word)
            pop()
            i = j
        } else {
            pushStyle(SpanStyle(color = Color(term.muted), fontFamily = CodeFont))
            append(text[i])
            pop()
            i++
        }
    }
}

@Composable
private fun PermissionCard(ask: PermissionAsk, engine: Engine) {
    val term = LocalTerm.current
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).border(1.dp, term.hair(), RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(term.bubble()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Permission", color = term.ink(), fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.semantics { heading() })
        Text(
            ask.action + if (ask.resources.isBlank()) "" else "  " + ask.resources,
            color = term.ink(),
            fontFamily = CodeFont,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("Nothing runs until you choose.", color = term.dim(), fontFamily = UiFont, fontSize = 12.sp, maxLines = 1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniButton("Allow once", { engine.permission(ask.id, "once") }, primary = true, modifier = Modifier.weight(1.3f))
            MiniButton("Always", { engine.permission(ask.id, "always") }, modifier = Modifier.weight(1f))
            MiniButton("Reject", { engine.permission(ask.id, "reject") }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val term = LocalTerm.current
    Box(
        modifier.height(32.dp).border(1.dp, term.hair(), RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(if (primary) term.ink() else term.paper()).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) term.paper() else term.ink(), fontFamily = UiFont, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun ComposerBar(
    draft: String,
    onDraft: (String) -> Unit,
    running: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAdd: () -> Unit,
) {
    val term = LocalTerm.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).height(52.dp).border(1.dp, term.hair(), RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(term.bubble()).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).border(1.dp, term.hair(), CircleShape).clip(CircleShape).clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = "Add. Attachments are not in this prototype." },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = term.ink(), fontFamily = UiFont, fontSize = 22.sp)
        }
        BasicTextField(
            value = draft,
            onValueChange = onDraft,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp).semantics { contentDescription = "Message" },
            textStyle = TextStyle(color = term.ink(), fontFamily = UiFont, fontSize = 16.sp),
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            decorationBox = { inner ->
                Box {
                    if (draft.isEmpty()) Text("Message", color = term.dim(), fontFamily = UiFont, fontSize = 16.sp)
                    inner()
                }
            },
        )
        Box(
            Modifier
                .size(36.dp)
                .border(1.dp, term.hair(), CircleShape)
                .clip(CircleShape)
                .background(term.ink())
                .clickable(role = Role.Button) { if (running) onStop() else onSend() }
                .semantics { contentDescription = if (running) "Stop" else "Send" },
            contentAlignment = Alignment.Center,
        ) {
            if (running) {
                Box(Modifier.size(12.dp).background(term.paper()))
            } else {
                SendMark(term.paper())
            }
        }
    }
}

@Composable
private fun SendMark(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), strokeWidth = 2.dp.toPx())
        drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.86f, h * 0.5f), strokeWidth = 2.dp.toPx())
        drawLine(color, Offset(w * 0.5f, h * 0.82f), Offset(w * 0.86f, h * 0.5f), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun AboutPane(model: CompanionState, engine: Engine, choice: ThemeChoice, onTheme: (ThemeChoice) -> Unit) {
    val term = LocalTerm.current
    val manual by engine.reactionManual.collectAsState()
    val temp by engine.reactionTemp.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = SectionGap)) {
        Text(
            "Back",
            color = term.ink(),
            fontFamily = UiFont,
            fontSize = 15.sp,
            modifier = Modifier.clickable(role = Role.Button) { engine.screen("connect") }.padding(bottom = Stack),
        )
        PillButton("Open demo fixtures") { engine.demo() }
        Spacer(Modifier.height(SectionGap))
        model.about.forEachIndexed { index, line ->
            val credit = line == CREDIT
            Text(
                line,
                color = if (credit) term.ink() else term.dim(),
                fontFamily = if (credit) UiFont else UiFont,
                fontWeight = if (credit) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = if (credit || index == 0) 20.sp else 15.sp,
                modifier = Modifier.padding(bottom = 10.dp).widthIn(max = 640.dp).semantics {
                    if (credit) contentDescription = CREDIT
                    if (index == 0 || credit) heading()
                },
            )
        }
        Text(FONT_NOTICE, color = term.dim(), fontFamily = UiFont, fontSize = 13.sp, modifier = Modifier.padding(bottom = Stack))
        ThemePicker(choice, onTheme)
        Spacer(Modifier.height(SectionGap))
        Text("Reactions", color = term.ink(), fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.semantics { heading() })
        Text(
            "Local marks only. OpenCode has no reaction sync. Temperature %.2f, %s. 0 is quiet, 1 is quick. Adaptive may follow an [[rx-temp]] mark. Manual ignores it.".format(temp, if (manual) "manual" else "adaptive"),
            color = term.dim(),
            fontFamily = UiFont,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth().height(Control), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Stack)) {
            TermButton("−", { engine.nudgeTemperature(-0.05f) })
            TermButton("+", { engine.nudgeTemperature(0.05f) })
            TermButton(if (manual) "Manual" else "Adaptive", { engine.setReactionMode(!manual) }, primary = manual)
        }
    }
}

@Composable
private fun SessionRow(item: NamedItem, project: String, onClick: () -> Unit) {
    val term = LocalTerm.current
    val mark = item.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
    val whenText = relativeTime(item.whenEpoch)
    val meta = listOf(project, whenText).filter { it.isNotBlank() }.joinToString(" · ")
    val active = item.flag == 1
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, term.hair(), RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(term.bubble()).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).border(1.dp, term.hair(), RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(term.paper()),
            contentAlignment = Alignment.Center,
        ) {
            Text(mark, color = term.ink(), fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.title.ifBlank { "Untitled session" }, color = term.ink(), fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) Text(meta, color = term.dim(), fontFamily = UiFont, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) term.mark() else term.dim()))
            Text(if (active) "Active" else "Idle", color = term.dim(), fontFamily = CodeFont, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun projectName(model: CompanionState, session: NamedItem): String {
    val hit = model.projects.firstOrNull { it.extra == session.extra && session.extra.isNotBlank() }
    return hit?.title?.takeIf { it.isNotBlank() } ?: ""
}

private fun relativeTime(epoch: Long, now: Long = System.currentTimeMillis()): String {
    if (epoch <= 0L) return ""
    val ms = if (epoch < 10_000_000_000L) epoch * 1000 else epoch
    val delta = (now - ms).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        delta < minute -> "Just now"
        delta < hour -> "${delta / minute}m"
        delta < day -> "${delta / hour}h"
        delta < 7 * day -> "${delta / day}d"
        else -> "${delta / day}d"
    }
}

@Composable
private fun TermButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    fill: Boolean = false,
) {
    val term = LocalTerm.current
    Box(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .height(Control)
            .border(1.dp, term.hair(), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) term.ink() else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) term.paper() else term.ink(), fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun TermToggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val term = LocalTerm.current
    Row(Modifier.fillMaxWidth().height(Control), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = term.ink(), fontFamily = UiFont, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TermButton(if (on) "On" else "Off", { onChange(!on) }, primary = on)
    }
}

@Composable
internal fun TermField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    singleLine: Boolean = true,
) {
    val term = LocalTerm.current
    var focused by remember { mutableStateOf(false) }
    val frame = Modifier.border(1.dp, if (focused) term.mark() else term.hair(), RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
    Column(modifier.fillMaxWidth().then(frame).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, color = term.dim(), fontFamily = CodeFont, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 28.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
                textStyle = TextStyle(color = term.ink(), fontFamily = UiFont, fontSize = 16.sp),
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 6,
                cursorBrush = SolidColor(Color.Transparent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            )
        }
    }
}

@Composable
private fun BlockCursor(color: Color, blink: Boolean, description: String) {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(blink) {
        on = true
        if (!blink) return@LaunchedEffect
        while (true) {
            delay(530)
            on = !on
        }
    }
    Box(
        Modifier.padding(start = 2.dp).size(width = 8.dp, height = 16.dp).background(if (on) color else Color.Transparent).semantics {
            contentDescription = description
        },
    )
}
