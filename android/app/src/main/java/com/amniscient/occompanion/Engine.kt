// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import android.app.Application
import android.util.Log
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class Engine(app: Application) : AndroidViewModel(app) {
    private companion object {
        const val TAG = "CompanionPair"
    }
    private val rxPrefs = app.getSharedPreferences("companion-rx", android.content.Context.MODE_PRIVATE)
    private val pairPrefs = app.getSharedPreferences("companion-pair", android.content.Context.MODE_PRIVATE)
    private val reactions = ReactionBook.restore(loadReactionMap(rxPrefs))
    private val _reactions = MutableStateFlow(reactions.export())
    private var rxManual = rxPrefs.getBoolean("manual", false)
    private var rxTemp = rxPrefs.getFloat("temp", 0.65f)
    private val _rxManual = MutableStateFlow(rxManual)
    private val _rxTemp = MutableStateFlow(rxTemp)
    private val gate = Mutex()
    private val _state = MutableStateFlow(CompanionState())
    val state: StateFlow<CompanionState> = _state
    private var streamJob: Job? = null
    @Volatile private var live: HttpURLConnection? = null
    private var booted = false

    fun boot(start: String?) {
        if (booted) return
        booted = true
        when (start) {
            "demo" -> demo()
            "about" -> submit("""{"op":"boot"}""", resetStream = true) {
                submit("""{"op":"ui","action":"screen","name":"about"}""", resetStream = false)
            }
            else -> submit("""{"op":"boot"}""", resetStream = true)
        }
    }

    fun lastHost(): String = pairPrefs.getString("host", "") ?: ""

    fun pair(raw: String, hostHint: String = lastHost()) {
        val chars = raw.length
        val hint = hostHint.ifBlank { lastHost() }
        Log.d(TAG, "paste submit chars=$chars hostSet=${hint.isNotBlank()}")
        when (val read = Pairing.readPaste(raw, hint, System.currentTimeMillis() / 1000)) {
            is Pairing.Read.Bad -> {
                Log.d(TAG, "paste rejected chars=$chars reason=${read.message}")
                if (read.message == Pairing.LOCALHOST) {
                    Log.w(TAG, "pairing payload has no address this phone can reach")
                }
                _state.value = _state.value.copy(error = read.message, status = "error")
            }
            is Pairing.Read.Ok -> viewModelScope.launch(Dispatchers.IO) {
                val remembered = Pairing.normalizeHost(hostHint) ?: read.ticket.serverUrl
                pairPrefs.edit().putString("host", remembered.removePrefix("http://").removePrefix("https://")).apply()
                Log.d(TAG, "paste accepted host=${hostOnly(read.ticket.serverUrl)} attempts=${read.ticket.attempts.size}")
                _state.value = _state.value.copy(error = "", status = "connecting")
                val attempts = read.ticket.attempts.ifEmpty { listOf(read.ticket) }
                var sawUsed = false
                var sawInvalid = false
                var secret = attempts.first().secret
                for (ticket in attempts) {
                    secret = ticket.secret
                    val result = HttpClient.redeem(ticket.method, ticket.redeemUrl, ticket.body, ticket.secret)
                    when (val done = Pairing.interpret(result.status, result.body)) {
                        is Pairing.Redeemed.Token -> {
                            val chars = done.value.toCharArray()
                            connect(ticket.serverUrl, ticket.local, chars)
                            return@launch
                        }
                        is Pairing.Redeemed.Bad -> when (done.message) {
                            Pairing.USED, Pairing.EXPIRED -> sawUsed = true
                            Pairing.UNREACHABLE -> Log.w(TAG, "pairing host unreachable ${hostOnly(ticket.redeemUrl)}")
                            else -> sawInvalid = true
                        }
                    }
                }
                val message = when {
                    sawUsed -> Pairing.USED
                    sawInvalid -> Pairing.INVALID
                    else -> Pairing.LOCALHOST
                }
                _state.value = _state.value.copy(error = Pairing.scrub(message, secret), status = "error")
            }
        }
    }

    private fun hostOnly(url: String): String {
        val origin = Policy.classify(url, true) ?: return "unknown"
        return "${origin.host}:${origin.port}"
    }

    fun connect(url: String, local: Boolean, password: CharArray) {
        if (password.isEmpty()) {
            Log.d(TAG, "connect rejected empty password")
            _state.value = _state.value.copy(error = "Enter the server password or pairing token. It stays in the Keystore.", status = "error")
            return
        }
        val origin = Policy.classify(url.trim(), local)
        if (origin == null) {
            Log.d(TAG, "connect rejected address")
            password.fill('\u0000')
            _state.value = _state.value.copy(
                error = "Enter the computer's address, such as 192.168.1.20:49374. Turn on Local HTTP for a private address.",
                status = "error",
            )
            return
        }
        SecretStore.save(getApplication(), password)
        password.fill('\u0000')
        val event = JSONObject().put("op", "connect").put("url", url.trim()).put("local", local).toString()
        submit(event, resetStream = true)
    }

    fun demo() = submit("""{"op":"demo"}""", resetStream = true)

    fun clearSecret() {
        SecretStore.clear(getApplication())
        _state.value = _state.value.copy(error = "Stored password cleared from this device.")
    }

    fun screen(name: String) {
        val event = JSONObject().put("op", "ui").put("action", "screen").put("name", name).toString()
        submit(event, resetStream = false)
    }

    fun openSession(id: String) {
        val event = JSONObject().put("op", "ui").put("action", "open-session").put("id", id).toString()
        submit(event, resetStream = true)
    }

    fun send(text: String) {
        val session = _state.value.activeSession
        val note = if (_state.value.demo) "" else reactions.pendingNote(session)
        if (note.isNotEmpty()) {
            reactions.ack(session)
            persistReactions()
            _reactions.value = reactions.export()
        }
        val event = JSONObject().put("op", "ui").put("action", "send").put("text", text)
        if (note.isNotEmpty()) event.put("wire", note + text)
        submit(event.toString(), resetStream = false)
    }

    fun react(text: String, emoji: String) {
        val session = _state.value.activeSession
        if (session.isEmpty()) return
        reactions.toggle(session, text, emoji)
        persistReactions()
        _reactions.value = reactions.export()
    }

    fun reactionChip(text: String): String? = _reactions.value[_state.value.activeSession]?.get(rxHash(text))?.emoji

    fun setReactionMode(manual: Boolean) {
        rxManual = manual
        persistReactions()
        _rxManual.value = manual
    }

    fun nudgeTemperature(delta: Float) {
        rxTemp = clampTemp(rxTemp + delta)
        persistReactions()
        _rxTemp.value = rxTemp
    }

    val reactionManual: StateFlow<Boolean> get() = _rxManual
    val reactionTemp: StateFlow<Float> get() = _rxTemp
    val reactionMap: StateFlow<Map<String, Map<String, RxHit>>> get() = _reactions

    fun interrupt() = submit("""{"op":"ui","action":"interrupt"}""", resetStream = false)

    fun permission(id: String, decision: String) {
        val event = JSONObject()
            .put("op", "ui")
            .put("action", "permission")
            .put("id", id)
            .put("decision", decision)
            .toString()
        submit(event, resetStream = false)
    }

    fun reconnect() = submit("""{"op":"ui","action":"reconnect"}""", resetStream = true)

    fun dismiss() = submit("""{"op":"ui","action":"dismiss"}""", resetStream = false)

    private fun submit(event: String, resetStream: Boolean, after: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (resetStream) stopStream()
            gate.withLock { pump(event) }
            after?.invoke()
        }
    }

    private fun pump(event: String) {
        var model = parseState(HeliumBridge.handle(event))
        _state.value = model
        var steps = 0
        while (model.request != null && model.request?.stream != true && steps < 8) {
            val req = model.request ?: break
            val password = if (model.demo) null else SecretStore.load(getApplication())
            val result = HttpClient.exchange(req, model.local, password)
            val err = scrub(result.error, password)
            password?.fill('\u0000')
            val back = JSONObject()
                .put("op", "http")
                .put("status", result.status)
                .put("tag", req.tag)
                .put("body", result.body)
                .put("error", err)
                .toString()
            model = parseState(HeliumBridge.handle(back))
            _state.value = model
            steps++
        }
        val stream = model.request
        if (stream != null && stream.stream) {
            startStream(stream, model.local)
        }
    }

    private fun startStream(req: ServerRequest, local: Boolean) {
        streamJob?.cancel()
        live?.disconnect()
        viewModelScope.launch(Dispatchers.IO) {
            gate.withLock {
                _state.value = parseState(HeliumBridge.handle("""{"op":"stream","state":"open","error":""}"""))
            }
        }
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            val password = if (_state.value.demo) null else SecretStore.load(getApplication())
            val result = HttpClient.openStream(req, local, password, onOpen = { live = it }) { body ->
                viewModelScope.launch(Dispatchers.IO) {
                    gate.withLock {
                        val event = JSONObject().put("op", "sse").put("body", body).toString()
                        _state.value = parseState(HeliumBridge.handle(event))
                    }
                }
            }
            val err = scrub(result.error, password)
            password?.fill('\u0000')
            live = null
            gate.withLock {
                val event = JSONObject()
                    .put("op", "stream")
                    .put("state", if (err.isEmpty() && result.status in 200..299) "closed" else "error")
                    .put("error", err)
                    .toString()
                _state.value = parseState(HeliumBridge.handle(event))
            }
        }
    }

    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        live?.disconnect()
        live = null
    }

    private fun scrub(error: String, password: CharArray?): String {
        if (password == null || password.isEmpty()) return error.take(300)
        return error.replace(String(password), "••••").take(300)
    }

    override fun onCleared() {
        stopStream()
    }

    private fun persistReactions() {
        val root = JSONObject()
        reactions.export().forEach { (session, bag) ->
            val arr = org.json.JSONArray()
            bag.forEach { (hash, hit) ->
                arr.put(JSONObject().put("h", hash).put("e", hit.emoji).put("s", hit.snippet).put("p", hit.pending))
            }
            root.put(session, arr)
        }
        rxPrefs.edit().putString("book", root.toString()).putBoolean("manual", rxManual).putFloat("temp", rxTemp).apply()
    }
}

private fun loadReactionMap(prefs: SharedPreferences): Map<String, Map<String, RxHit>> {
    val raw = prefs.getString("book", null) ?: return emptyMap()
    return try {
        val root = JSONObject(raw)
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val session = keys.next()
                val arr = root.getJSONArray(session)
                val bag = linkedMapOf<String, RxHit>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    bag[o.getString("h")] = RxHit(o.getString("e"), o.optString("s"), o.optBoolean("p"))
                }
                put(session, bag)
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }
}
