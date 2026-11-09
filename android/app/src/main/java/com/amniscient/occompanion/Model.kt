// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import org.json.JSONObject

data class NamedItem(val title: String, val id: String, val extra: String, val flag: Int, val whenEpoch: Long = 0)

data class TranscriptRow(
    val role: String,
    val text: String,
    val lang: String,
    val name: String,
    val state: Int,
)

data class ServerRequest(
    val tag: String,
    val method: String,
    val path: String,
    val body: String,
    val stream: Boolean,
    val https: Boolean,
    val host: String,
    val port: Int,
)

data class PermissionAsk(
    val id: String,
    val action: String,
    val resources: String,
    val message: String,
)

data class CompanionState(
    val screen: String = "connect",
    val demo: Boolean = false,
    val banner: String = "",
    val status: String = "idle",
    val error: String = "",
    val credit: String = CREDIT,
    val unofficial: Boolean = true,
    val url: String = "",
    val local: Boolean = false,
    val running: Boolean = false,
    val activeSession: String = "",
    val composer: Boolean = false,
    val request: ServerRequest? = null,
    val projects: List<NamedItem> = emptyList(),
    val sessions: List<NamedItem> = emptyList(),
    val rows: List<TranscriptRow> = emptyList(),
    val permission: PermissionAsk? = null,
    val about: List<String> = listOf(CREDIT),
)

const val CREDIT = "Originally built by Amniscient"

fun parseState(json: String): CompanionState {
    val o = JSONObject(json)
    val request = if (o.isNull("request")) null else o.getJSONObject("request").let { r ->
        ServerRequest(
            tag = r.optString("tag"),
            method = r.optString("method"),
            path = r.optString("path"),
            body = r.optString("body"),
            stream = r.optBoolean("stream"),
            https = r.optBoolean("https", true),
            host = r.optString("host"),
            port = r.optInt("port"),
        )
    }
    val permission = if (o.isNull("permission")) null else o.getJSONObject("permission").let { p ->
        PermissionAsk(
            id = p.optString("id"),
            action = p.optString("action"),
            resources = p.optString("resources"),
            message = p.optString("message"),
        )
    }
    fun items(name: String): List<NamedItem> {
        val arr = o.optJSONArray(name) ?: return emptyList()
        return List(arr.length()) { i ->
            val it = arr.getJSONObject(i)
            NamedItem(it.optString("title"), it.optString("id"), it.optString("extra"), it.optInt("flag"), it.optLong("when"))
        }
    }
    val rowsJson = o.optJSONArray("rows")
    val rows = if (rowsJson == null) emptyList() else List(rowsJson.length()) { i ->
        val it = rowsJson.getJSONObject(i)
        TranscriptRow(it.optString("role"), it.optString("text"), it.optString("lang"), it.optString("name"), it.optInt("state"))
    }
    val aboutJson = o.optJSONArray("about")
    val about = if (aboutJson == null) listOf(o.optString("credit", CREDIT)) else List(aboutJson.length()) { aboutJson.getString(it) }
    return CompanionState(
        screen = o.optString("screen", "connect"),
        demo = o.optBoolean("demo"),
        banner = o.optString("banner"),
        status = o.optString("status"),
        error = o.optString("error"),
        credit = o.optString("credit", CREDIT),
        unofficial = o.optBoolean("unofficial", true),
        url = o.optString("url"),
        local = o.optBoolean("local"),
        running = o.optBoolean("running"),
        activeSession = o.optString("activeSession"),
        composer = o.optBoolean("composer"),
        request = request,
        projects = items("projects"),
        sessions = items("sessions"),
        rows = rows,
        permission = permission,
        about = about,
    )
}
