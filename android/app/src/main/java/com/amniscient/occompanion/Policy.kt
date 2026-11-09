// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

/**
 * Same origin rules as the Helium core. HTTPS is the default. HTTP is accepted
 * only when local development is on and the host is a private or loopback
 * address. This is not a certificate bypass.
 */
object Policy {
    data class Origin(val https: Boolean, val host: String, val port: Int)

    fun classify(url: String, local: Boolean): Origin? {
        val raw = url.trim()
        if (raw.isEmpty() || raw.length > 512 || raw.any { it.isWhitespace() }) return null
        val https = when {
            raw.startsWith("https://") -> true
            raw.startsWith("http://") -> false
            else -> return null
        }
        val rest = raw.substring(if (https) 8 else 7)
        val hostport = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostport.isEmpty() || hostport.contains('@')) return null
        val host: String
        val portText: String?
        if (hostport.startsWith("[")) {
            val end = hostport.indexOf(']')
            if (end < 0) return null
            host = hostport.substring(0, end + 1)
            val tail = hostport.substring(end + 1)
            portText = when {
                tail.isEmpty() -> null
                tail.startsWith(":") -> tail.substring(1)
                else -> return null
            }
        } else {
            val colon = hostport.lastIndexOf(':')
            if (colon >= 0 && hostport.indexOf(':') == colon) {
                host = hostport.substring(0, colon)
                portText = hostport.substring(colon + 1)
            } else if (colon >= 0) {
                return null
            } else {
                host = hostport
                portText = null
            }
        }
        if (host.isEmpty()) return null
        val port = if (portText == null) {
            if (https) 443 else 80
        } else {
            val v = portText.toIntOrNull() ?: return null
            if (v !in 1..65535) return null
            v
        }
        if (!https && (!local || !privateHost(host))) return null
        return Origin(https, host, port)
    }

    fun privateHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" || host == "[::1]") {
            return true
        }
        val parts = host.split('.')
        if (parts.size != 4) return false
        val nums = parts.map { it.toIntOrNull() ?: return false }
        if (nums.any { it !in 0..255 }) return false
        val a = nums[0]
        val b = nums[1]
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) || (a == 169 && b == 254) || (a == 100 && b in 64..127)
    }
}
