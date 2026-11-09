// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import android.util.Base64
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class HttpResult(val status: Int, val body: String, val error: String)

object HttpClient {
    private const val TAG = "CompanionPair"
    private const val UA = "AmniscientCompanion/0.1.0 (unofficial; not OpenCode)"

    fun redeem(method: String, redeemUrl: String, body: String, secret: String): HttpResult {
        val origin = Policy.classify(redeemUrl, true) ?: run {
            Log.w(TAG, "redeem refused an address that is not a private host")
            return HttpResult(0, "", Pairing.INVALID)
        }
        val afterScheme = redeemUrl.substringAfter("://")
        val slash = afterScheme.indexOf('/')
        val path = if (slash < 0) "/" else afterScheme.substring(slash).substringBefore('?').substringBefore('#')
        if (!path.startsWith("/") || path.contains("..")) return HttpResult(0, "", Pairing.INVALID)
        var conn: HttpURLConnection? = null
        try {
            val scheme = if (origin.https) "https" else "http"
            val host = origin.host.removePrefix("[").removeSuffix("]")
            conn = (URL("$scheme://$host:${origin.port}$path").openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 4_000
                readTimeout = 6_000
                requestMethod = if (method == "POST") "POST" else "GET"
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Connection", "close")
            }
            if (method == "POST") {
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty().take(8_000)
            return HttpResult(status, text, "")
        } catch (e: Exception) {
            Log.w(TAG, "redeem network failure ${origin.host}:${origin.port} ${e.javaClass.simpleName}")
            val message = Pairing.scrub(e.javaClass.simpleName + ": " + (e.message ?: "network error"), secret)
            return HttpResult(0, "", message)
        } finally {
            conn?.disconnect()
        }
    }

    fun exchange(req: ServerRequest, local: Boolean, password: CharArray?): HttpResult {
        val gate = gate(req, local) ?: return HttpResult(0, "", "Refusing a cleartext or non-HTTP request.")
        var conn: HttpURLConnection? = null
        try {
            conn = open(gate, req, password, stream = false)
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty().take(400_000)
            return HttpResult(status, body, "")
        } catch (e: Exception) {
            return HttpResult(0, "", e.javaClass.simpleName + ": " + (e.message ?: "network error"))
        } finally {
            conn?.disconnect()
        }
    }

    fun openStream(
        req: ServerRequest,
        local: Boolean,
        password: CharArray?,
        onOpen: (HttpURLConnection) -> Unit,
        onEvent: (String) -> Unit,
    ): HttpResult {
        val gate = gate(req, local) ?: return HttpResult(0, "", "Refusing a cleartext or non-HTTP request.")
        var conn: HttpURLConnection? = null
        try {
            conn = open(gate, req, password, stream = true)
            onOpen(conn)
            val status = conn.responseCode
            if (status == 401) return HttpResult(401, "", "Unauthorized.")
            if (status !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(400)
                return HttpResult(status, "", if (err.isBlank()) "Event stream HTTP $status" else err)
            }
            conn.inputStream.bufferedReader().use { reader ->
                val data = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").removePrefix(" "))
                        }
                        line.isEmpty() && data.isNotEmpty() -> {
                            onEvent(data.toString())
                            data.clear()
                        }
                    }
                }
            }
            return HttpResult(status, "", "")
        } catch (e: Exception) {
            return HttpResult(0, "", e.javaClass.simpleName + ": " + (e.message ?: "stream closed"))
        } finally {
            conn?.disconnect()
        }
    }

    private fun gate(req: ServerRequest, local: Boolean): OriginGate? {
        if (req.path.isEmpty() || !req.path.startsWith("/")) return null
        if (!req.https && (!local || !Policy.privateHost(req.host))) return null
        if (req.host.isEmpty() || req.port !in 1..65535) return null
        return OriginGate(req.https, req.host, req.port, req.path)
    }

    private fun open(gate: OriginGate, req: ServerRequest, password: CharArray?, stream: Boolean): HttpURLConnection {
        val scheme = if (gate.https) "https" else "http"
        val url = URL("$scheme://${gate.host}:${gate.port}${gate.path}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 8_000
            readTimeout = if (stream) 0 else 20_000
            requestMethod = if (req.method == "POST") "POST" else "GET"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", if (stream) "text/event-stream" else "application/json")
            setRequestProperty("Connection", "close")
        }
        if (password != null && password.isNotEmpty()) {
            val raw = "opencode:" + String(password)
            val token = Base64.encodeToString(raw.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $token")
        }
        if (req.method == "POST") {
            val bytes = req.body.toByteArray(StandardCharsets.UTF_8)
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
        }
        return conn
    }

    private data class OriginGate(val https: Boolean, val host: String, val port: Int, val path: String)
}
