// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import org.json.JSONObject
import java.util.Base64

/**
 * Pairing payloads the phone can read.
 *
 * Version 1 is this companion's helper: url, one-time secret, and expiry.
 * The other shapes are the ones public OpenCode sources describe. See docs/PAIRING.md.
 */
object Pairing {
    data class Ticket(
        val serverUrl: String,
        val local: Boolean,
        val method: String,
        val redeemUrl: String,
        val body: String,
        val secret: String,
        val attempts: List<Ticket> = emptyList(),
    )

    sealed class Read {
        data class Ok(val ticket: Ticket) : Read()
        data class Bad(val message: String) : Read()
    }

    sealed class Redeemed {
        data class Token(val value: String) : Redeemed()
        data class Bad(val message: String) : Redeemed()
    }

    const val EXPIRED = "This pairing code has expired."
    const val USED = "This pairing code has expired or was already used."
    const val INVALID = "This pairing code is not valid."
    const val UNREACHABLE = "The server is unreachable."
    const val NO_TOKEN = "The server did not return a pairing token."
    const val LOCALHOST = "OpenCode is only listening on this computer. Enter this computer's address above, or run opencode pair --url http://<pc-ip>:<port>."
    const val NEED_HOST = "This is only the code. Enter the computer's address, such as 192.168.1.20:49374."
    const val PASTE_EMPTY = "Paste the pairing code or link."
    const val NOT_A_CODE = "Could not find a pairing code in that text."
    const val NO_CAMERA = "Camera permission is off. Allow the camera to scan, or paste the link."
    const val CAMERA_BLOCKED = "Camera permission was denied. Paste the link, or allow the camera in system settings."
    const val UNREADABLE = "Could not read a QR code. Paste the link from the terminal."

    fun read(raw: String, nowEpochSeconds: Long, hostHint: String = ""): Read {
        val text = unwrap(raw.trim().trim('"'))
        if (text.isEmpty()) return Read.Bad(INVALID)
        connectUrl(text)?.let { return ticketsForOfficial(listOf(it), it.substringAfterLast('/'), hostHint) }
        val json = try {
            JSONObject(text)
        } catch (_: Exception) {
            return Read.Bad(INVALID)
        }
        if (json.has("v")) {
            if (json.optInt("v", -1) != 1) return Read.Bad(INVALID)
            val url = json.optString("url").trim()
            val secret = json.optString("secret").trim()
            if (!json.has("exp") || secret.length !in 8..128 || !safeToken(secret)) return Read.Bad(INVALID)
            val exp = json.optLong("exp", 0L)
            if (exp <= nowEpochSeconds) return Read.Bad(EXPIRED)
            val origin = Policy.classify(url, true) ?: return Read.Bad(INVALID)
            val local = !origin.https
            val redeem = json.optString("redeem").trim().ifEmpty { originOf(origin) + "/pair/redeem" }
            if (Policy.classify(redeem, true) == null) return Read.Bad(INVALID)
            val body = JSONObject().put("secret", secret).toString()
            val ticket = Ticket(originOf(origin), local, "POST", stripToPath(redeem), body, secret)
            return Read.Ok(ticket.copy(attempts = listOf(ticket)))
        }
        val code = json.optString("code").trim()
        val urls = json.optJSONArray("urls") ?: return Read.Bad(INVALID)
        if (!safeToken(code)) return Read.Bad(INVALID)
        val choices = buildList {
            for (i in 0 until urls.length()) add(urls.optString(i))
        }
        return ticketsForOfficial(choices, code, hostHint)
    }

    /**
     * What a person pastes from `opencode pair`: a bare code, `{code,urls}`, a connect
     * link, or the whole terminal printout with blank lines around it.
     * [hostHint] is the computer-address field or a remembered address. A bare code uses it
     * as the whole server. A loopback link keeps its code, port, and path, and this host replaces 127.0.0.1.
     */
    fun readPaste(raw: String, hostHint: String, nowEpochSeconds: Long): Read {
        val text = raw.replace("\r\n", "\n").trim()
        if (text.isEmpty()) return Read.Bad(PASTE_EMPTY)
        val links = connectLinks(text)
        if (links.isNotEmpty()) {
            val code = links.first().substringAfterLast('/')
            return ticketsForOfficial(links, code, hostHint)
        }
        val fragment = fragmentLink(text)
        if (fragment != null) return read(fragment, nowEpochSeconds, hostHint)
        val json = jsonObject(text)
        if (json != null) return read(json, nowEpochSeconds, hostHint)
        if (text.contains('%') || text.startsWith("http://") || text.startsWith("https://") || text.startsWith("{")) {
            val direct = read(text, nowEpochSeconds, hostHint)
            if (direct is Read.Ok || (direct is Read.Bad && direct.message != INVALID)) return direct
        }
        val code = bareCode(text)
        if (code != null) {
            val host = normalizeHost(hostHint) ?: return Read.Bad(NEED_HOST)
            return ticketsForOfficial(listOf(host), code)
        }
        return Read.Bad(NOT_A_CODE)
    }

    fun normalizeHost(hint: String): String? {
        var text = hint.trim().trimEnd('/')
        if (text.isEmpty() || text == "http://" || text == "https://") return null
        if (!text.startsWith("http://") && !text.startsWith("https://")) text = "http://$text"
        val scheme = text.substringBefore("://")
        val rest = text.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (rest.isEmpty()) return null
        val origin = Policy.classify("$scheme://$rest", true) ?: return null
        return originOf(origin)
    }

    fun interpret(status: Int, body: String): Redeemed {
        if (status == 0) return Redeemed.Bad(UNREACHABLE)
        if (status == 401 || status == 410) return Redeemed.Bad(USED)
        if (status == 404) return Redeemed.Bad(INVALID)
        if (status !in 200..299) return Redeemed.Bad(INVALID)
        val token = try {
            val o = JSONObject(body)
            o.optString("token").ifBlank { o.optString("password") }
        } catch (_: Exception) {
            ""
        }
        if (token.length !in 8..256) return Redeemed.Bad(NO_TOKEN)
        return Redeemed.Token(token)
    }

    fun scrub(text: String, secret: String): String {
        if (secret.isEmpty()) return text.take(300)
        return text.replace(secret, "••••").take(300)
    }

    private fun ticketsForOfficial(urls: List<String>, code: String, hostHint: String = ""): Read {
        if (!safeToken(code)) return Read.Bad(INVALID)
        val parsed = urls.map { it.trim() }.filter { Policy.classify(it, true) != null }
        if (parsed.isEmpty()) return Read.Bad(INVALID)
        val remote = parsed.filter { !isLoopback(it) }
        if (remote.isEmpty()) {
            val swapped = swapLoopback(parsed, hostHint) ?: return Read.Bad(LOCALHOST)
            return ticketsForOfficial(swapped, code, "")
        }
        val attempts = remote.map { base ->
            val origin = Policy.classify(base, true)!!
            val local = !origin.https
            val redeem = if (base.contains("/auth/connect/")) {
                base.substringBefore('?').substringBefore('#')
            } else {
                originOf(origin) + "/auth/connect/" + code
            }
            Ticket(originOf(origin), local, "GET", redeem, "", code)
        }
        return Read.Ok(attempts.first().copy(attempts = attempts))
    }

    private fun ticketForOfficial(baseOrFull: String, code: String): Read {
        if (!safeToken(code)) return Read.Bad(INVALID)
        val origin = Policy.classify(baseOrFull, true) ?: return Read.Bad(INVALID)
        val local = !origin.https
        val redeem = if (baseOrFull.contains("/auth/connect/")) {
            baseOrFull.substringBefore('?').substringBefore('#')
        } else {
            originOf(origin) + "/auth/connect/" + code
        }
        val ticket = Ticket(originOf(origin), local, "GET", redeem, "", code)
        return Read.Ok(ticket.copy(attempts = listOf(ticket)))
    }

    private fun swapLoopback(urls: List<String>, hostHint: String): List<String>? {
        val hint = normalizeHost(hostHint) ?: return null
        if (isLoopback(hint)) return null
        val hintOrigin = Policy.classify(hint, true) ?: return null
        val host = hintOrigin.host.removePrefix("[").removeSuffix("]")
        return urls.map { base ->
            val origin = Policy.classify(base, true) ?: return@map base
            val scheme = if (origin.https) "https" else "http"
            if (base.contains("/auth/connect/")) {
                val codePart = base.substringAfter("/auth/connect/").substringBefore('?').substringBefore('#')
                "$scheme://$host:${origin.port}/auth/connect/$codePart"
            } else {
                "$scheme://$host:${origin.port}"
            }
        }
    }

    private fun isLoopback(url: String): Boolean {
        return url.contains("127.0.0.1") || url.contains("://localhost") || url.contains("[::1]")
    }

    private fun unwrap(raw: String): String {
        var text = raw
        if (text.contains('%')) {
            try {
                val decoded = java.net.URLDecoder.decode(text, "UTF-8")
                if (decoded != text) text = decoded.trim()
            } catch (_: Exception) {
            }
        }
        val marker = "opencode.ai/pair#"
        val at = text.indexOf(marker)
        if (at < 0) return text
        val frag = text.substring(at + marker.length).substringBefore('?').trim()
        if (frag.isEmpty()) return text
        return try {
            val padded = frag.replace('-', '+').replace('_', '/') + "=".repeat((4 - frag.length % 4) % 4)
            String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        } catch (_: Exception) {
            text
        }
    }

    private fun connectLinks(text: String): List<String> {
        val found = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val http = text.indexOf("http://", i)
            val https = text.indexOf("https://", i)
            val at = when {
                http < 0 -> https
                https < 0 -> http
                else -> minOf(http, https)
            }
            if (at < 0) break
            var end = at
            while (end < text.length && !text[end].isWhitespace()) end += 1
            val candidate = text.substring(at, end).trimEnd('.', ',', ')', ']', '"', '\'')
            val link = connectUrl(candidate)
            if (link != null && link !in found) found.add(link)
            i = end + 1
        }
        return found
    }

    private fun fragmentLink(text: String): String? {
        val https = "https://opencode.ai/pair#"
        val http = "http://opencode.ai/pair#"
        val at = when {
            text.contains(https) -> text.indexOf(https)
            text.contains(http) -> text.indexOf(http)
            else -> return null
        }
        var end = at
        while (end < text.length && !text[end].isWhitespace()) end += 1
        return text.substring(at, end)
    }

    private fun jsonObject(text: String): String? {
        var i = text.indexOf('{')
        while (i >= 0) {
            val end = balancedObject(text, i)
            if (end > i) {
                val slice = text.substring(i, end)
                if (slice.contains("\"code\"") || slice.contains("\"v\"")) return slice
            }
            i = text.indexOf('{', i + 1)
        }
        return null
    }

    private fun balancedObject(text: String, start: Int): Int {
        var depth = 0
        var quote = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (quote) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') quote = false
                continue
            }
            when (c) {
                '"' -> quote = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return i + 1
                }
            }
        }
        return -1
    }

    private fun bareCode(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size != 1 || !safeToken(lines[0])) return null
        return lines[0]
    }

    private fun connectUrl(text: String): String? {
        if (!text.startsWith("http://") && !text.startsWith("https://")) return null
        val path = text.substringAfter("://").substringBefore('?').substringBefore('#')
        val marker = "/auth/connect/"
        val at = path.indexOf(marker)
        if (at < 0) return null
        val code = path.substring(at + marker.length)
        if (!safeToken(code) || code.contains('/')) return null
        return text.substringBefore('?').substringBefore('#')
    }

    private fun pick(urls: List<String>): String? {
        val ok = urls.map { it.trim() }.filter { Policy.classify(it, true) != null }
        val reachable = ok.filter { !it.contains("127.0.0.1") && !it.contains("://localhost") }
        return reachable.firstOrNull() ?: ok.firstOrNull()
    }

    private fun originOf(origin: Policy.Origin): String {
        val scheme = if (origin.https) "https" else "http"
        val host = origin.host.removePrefix("[").removeSuffix("]")
        return "$scheme://$host:${origin.port}"
    }

    private fun stripToPath(url: String): String = url.trim()

    private fun safeToken(value: String): Boolean {
        if (value.length !in 8..128) return false
        return value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
