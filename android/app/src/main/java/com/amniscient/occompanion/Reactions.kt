// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

/**
 * Local analogue of grok-remote's chat reactions (web/index.html, commit bda2d78).
 * The five marks, toggle-to-clear, FNV-1a text hash, and a 0–1 temperature with
 * adaptive versus manual lock. OpenCode has no reaction route, so nothing here
 * is synced to a server. Pending marks can ride the next real prompt as text.
 * The XR hologram reflexes in that repo are a different feature and are not ported.
 */
val RX_SET = listOf("👍", "👎", "😂", "😮", "❤")

data class RxHit(val emoji: String, val snippet: String, val pending: Boolean)

data class AgentMarks(val text: String, val stamp: String?, val temperature: Float?)

class ReactionBook {
    private val sessions = linkedMapOf<String, LinkedHashMap<String, RxHit>>()

    fun toggle(session: String, text: String, emoji: String) {
        if (session.isEmpty() || emoji.isBlank()) return
        val bag = sessions.getOrPut(session) { linkedMapOf() }
        val key = rxHash(text)
        val cur = bag[key]
        if (cur != null && cur.emoji == emoji) bag.remove(key)
        else bag[key] = RxHit(emoji, text.replace(Regex("\\s+"), " ").take(180), pending = true)
    }

    fun chip(session: String, text: String): String? = sessions[session]?.get(rxHash(text))?.emoji

    fun pendingNote(session: String): String {
        val pending = sessions[session]?.values?.filter { it.pending && it.emoji.isNotEmpty() }.orEmpty()
        if (pending.isEmpty()) return ""
        val lines = pending.joinToString(" · ") { it.emoji + " " + it.snippet.take(48) }
        return "[User reactions] $lines\nThese marks sit on earlier replies in this client. Silent signal only. Do not narrate them unless the reaction matters to the work.\n\n"
    }

    fun ack(session: String) {
        val bag = sessions[session] ?: return
        bag.keys.toList().forEach { key ->
            val hit = bag[key] ?: return@forEach
            if (hit.pending) bag[key] = hit.copy(pending = false)
        }
    }

    fun export(): Map<String, Map<String, RxHit>> = sessions.mapValues { it.value.toMap() }

    companion object {
        fun restore(raw: Map<String, Map<String, RxHit>>): ReactionBook {
            val book = ReactionBook()
            raw.forEach { (session, bag) ->
                book.sessions[session] = LinkedHashMap(bag)
            }
            return book
        }
    }
}

fun rxHash(text: String): String {
    var h = 2166136261L.toInt()
    val s = text.take(400)
    for (ch in s) {
        h = ((h xor ch.code).toLong() * 16777619L).toInt()
    }
    return h.toUInt().toString(16)
}

fun clampTemp(value: Float): Float = value.coerceIn(0f, 1f)

/** Agent temperature marks are ignored while the meter is manually locked. */
fun nextTemperature(current: Float, modeManual: Boolean, fromAgent: Boolean, proposed: Float): Float {
    if (fromAgent && modeManual) return clampTemp(current)
    return clampTemp(proposed)
}

fun parseAgentMarks(raw: String): AgentMarks {
    var temp: Float? = null
    var stamp: String? = null
    var text = raw
    val tempRe = Regex("""\[\[rx-temp:\s*([0-9.]+)\]\]""", RegexOption.IGNORE_CASE)
    text = tempRe.replace(text) { m ->
        m.groupValues[1].toFloatOrNull()?.let { temp = it }
        ""
    }
    val rxRe = Regex("""\[\[rx:([^\]]+)\]\]""", RegexOption.IGNORE_CASE)
    text = rxRe.replace(text) { m ->
        stamp = m.groupValues[1].trim()
        ""
    }
    text = Regex("""(?:^|\n)\s*RX:(\S+)\s*""").replace(text) { m ->
        stamp = m.groupValues[1].trim()
        "\n"
    }
    return AgentMarks(text.replace(Regex("[ \\t]{2,}"), " ").trim(), stamp, temp)
}
