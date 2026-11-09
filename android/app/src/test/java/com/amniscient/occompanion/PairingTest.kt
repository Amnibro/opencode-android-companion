// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {
    private val now = 1_700_000_000L

    @Test
    fun versionOneBuildsARedeemPost() {
        val raw = """{"v":1,"url":"http://10.0.2.2:8767","secret":"once-secret-1","exp":${now + 60}}"""
        val read = Pairing.read(raw, now) as Pairing.Read.Ok
        assertEquals("http://10.0.2.2:8767", read.ticket.serverUrl)
        assertEquals(true, read.ticket.local)
        assertEquals("POST", read.ticket.method)
        assertEquals("http://10.0.2.2:8767/pair/redeem", read.ticket.redeemUrl)
        assertTrue(read.ticket.body.contains("once-secret-1"))
    }

    @Test
    fun expiredCodeIsRefusedBeforeAnyRequest() {
        val raw = """{"v":1,"url":"http://10.0.2.2:8767","secret":"once-secret-1","exp":${now - 1}}"""
        val read = Pairing.read(raw, now) as Pairing.Read.Bad
        assertEquals(Pairing.EXPIRED, read.message)
        assertTrue(!read.message.contains("once-secret-1"))
    }

    @Test
    fun officialJsonUsesTheConnectPathAndSkipsLoopback() {
        val raw = """{"code":"f4GJvk8uW-WUlHaWFDSSLA","urls":["http://127.0.0.1:49374","http://10.0.2.2:49374"]}"""
        val read = Pairing.read(raw, now) as Pairing.Read.Ok
        assertEquals("GET", read.ticket.method)
        assertEquals("http://10.0.2.2:49374/auth/connect/f4GJvk8uW-WUlHaWFDSSLA", read.ticket.redeemUrl)
        assertEquals("http://10.0.2.2:49374", read.ticket.serverUrl)
        assertEquals(1, read.ticket.attempts.size)
    }

    @Test
    fun officialJsonTriesEveryReachableUrlAndExplainsLocalhost() {
        val both = """{"code":"f4GJvk8uW-WUlHaWFDSSLA","urls":["http://127.0.0.1:4096","http://10.0.2.2:4096","https://pair.example.com"]}"""
        val read = Pairing.read(both, now) as Pairing.Read.Ok
        assertEquals(2, read.ticket.attempts.size)
        assertEquals("http://10.0.2.2:4096/auth/connect/f4GJvk8uW-WUlHaWFDSSLA", read.ticket.attempts[0].redeemUrl)
        assertTrue(read.ticket.attempts[1].redeemUrl.startsWith("https://pair.example.com"))
        val onlyLoop = """{"code":"f4GJvk8uW-WUlHaWFDSSLA","urls":["http://127.0.0.1:4096","http://localhost:4096"]}"""
        val loop = Pairing.read(onlyLoop, now) as Pairing.Read.Bad
        assertEquals(Pairing.LOCALHOST, loop.message)
        assertTrue(loop.message.contains("opencode pair --url http://<pc-ip>:<port>"))
        val printedLoop = Pairing.read("http://127.0.0.1:49374/auth/connect/printed-code-1", now) as Pairing.Read.Bad
        assertEquals(Pairing.LOCALHOST, printedLoop.message)
        val encoded = java.net.URLEncoder.encode(both, "UTF-8")
        val decoded = Pairing.read(encoded, now) as Pairing.Read.Ok
        assertEquals(read.ticket.redeemUrl, decoded.ticket.redeemUrl)
    }

    @Test
    fun loopbackLinkKeepsCodeAndPortAndUsesComputerAddress() {
        val link = "http://127.0.0.1:49374/auth/connect/printed-code-1"
        val swapped = Pairing.readPaste("  $link \n", "192.168.1.20", now) as Pairing.Read.Ok
        assertEquals("http://192.168.1.20:49374/auth/connect/printed-code-1", swapped.ticket.redeemUrl)
        assertEquals("http://192.168.1.20:49374", swapped.ticket.serverUrl)
        val hintPortIgnored = Pairing.readPaste(link, "100.64.0.8:80", now) as Pairing.Read.Ok
        assertEquals("http://100.64.0.8:49374/auth/connect/printed-code-1", hintPortIgnored.ticket.redeemUrl)
        val json = """{"code":"printed-code-1","urls":["http://127.0.0.1:49374"]}"""
        val fromJson = Pairing.readPaste("\n$json\n", "192.168.1.20:49374", now) as Pairing.Read.Ok
        assertEquals("http://192.168.1.20:49374/auth/connect/printed-code-1", fromJson.ticket.redeemUrl)
        val alone = Pairing.readPaste(link, "", now) as Pairing.Read.Bad
        assertEquals(Pairing.LOCALHOST, alone.message)
        assertTrue(alone.message.contains("opencode pair --url"))
    }

    @Test
    fun pasteFormsFromOpencodePair() {
        val code = "printed-code-1"
        val bare = Pairing.readPaste("  $code  \n", "192.168.1.20:49374", now) as Pairing.Read.Ok
        assertEquals("http://192.168.1.20:49374/auth/connect/$code", bare.ticket.redeemUrl)
        val noHost = Pairing.readPaste("\n$code\n", "  ", now) as Pairing.Read.Bad
        assertEquals(Pairing.NEED_HOST, noHost.message)
        val json = "\n  {\"code\":\"f4GJvk8uW-WUlHaWFDSSLA\",\"urls\":[\"http://192.168.1.20:49374\",\"http://127.0.0.1:49374\"]} \n"
        val fromJson = Pairing.readPaste(json, "", now) as Pairing.Read.Ok
        assertEquals("http://192.168.1.20:49374/auth/connect/f4GJvk8uW-WUlHaWFDSSLA", fromJson.ticket.redeemUrl)
        val banner = """
            Open a link to connect. Links work once and expire in 5 minutes.

            http://127.0.0.1:49374/auth/connect/$code
            http://192.168.1.20:49374/auth/connect/$code

        """.trimIndent()
        val fromBanner = Pairing.readPaste(banner, "", now) as Pairing.Read.Ok
        assertEquals("http://192.168.1.20:49374/auth/connect/$code", fromBanner.ticket.redeemUrl)
        val mesh = Pairing.readPaste("\nhttp://100.64.0.8:49374/auth/connect/$code\n", "", now) as Pairing.Read.Ok
        assertEquals("http://100.64.0.8:49374/auth/connect/$code", mesh.ticket.redeemUrl)
        val encoded = java.net.URLEncoder.encode(
            """{"code":"f4GJvk8uW-WUlHaWFDSSLA","urls":["http://192.168.1.20:49374"]}""",
            "UTF-8",
        )
        val fromEncoded = Pairing.readPaste("  $encoded\n", "", now) as Pairing.Read.Ok
        assertEquals("http://192.168.1.20:49374/auth/connect/f4GJvk8uW-WUlHaWFDSSLA", fromEncoded.ticket.redeemUrl)
        assertEquals(Pairing.PASTE_EMPTY, (Pairing.readPaste(" \n\t ", "", now) as Pairing.Read.Bad).message)
        assertEquals(Pairing.NOT_A_CODE, (Pairing.readPaste("Open a link to connect.", "", now) as Pairing.Read.Bad).message)
    }

    @Test
    fun printedConnectLinkIsAccepted() {
        val raw = "http://192.168.1.20:49374/auth/connect/printed-code-1"
        val read = Pairing.read(raw, now) as Pairing.Read.Ok
        assertEquals(raw, read.ticket.redeemUrl)
        assertEquals(true, read.ticket.local)
    }

    @Test
    fun pairFragmentDecodesToTheSameJson() {
        val json = """{"code":"fragment-code-1","urls":["https://pair.example.com"]}"""
        val frag = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        val read = Pairing.read("https://opencode.ai/pair#$frag", now) as Pairing.Read.Ok
        assertEquals("https://pair.example.com:443/auth/connect/fragment-code-1", read.ticket.redeemUrl)
        assertEquals(false, read.ticket.local)
    }

    @Test
    fun junkAndPublicHttpAreInvalid() {
        assertEquals(Pairing.INVALID, (Pairing.read("hello", now) as Pairing.Read.Bad).message)
        val raw = """{"v":1,"url":"http://8.8.8.8","secret":"once-secret-1","exp":${now + 30}}"""
        assertEquals(Pairing.INVALID, (Pairing.read(raw, now) as Pairing.Read.Bad).message)
    }

    @Test
    fun redeemResponses() {
        assertEquals("stored-token", (Pairing.interpret(200, """{"token":"stored-token"}""") as Pairing.Redeemed.Token).value)
        assertEquals(Pairing.USED, (Pairing.interpret(410, "") as Pairing.Redeemed.Bad).message)
        assertEquals(Pairing.USED, (Pairing.interpret(401, "") as Pairing.Redeemed.Bad).message)
        assertEquals(Pairing.INVALID, (Pairing.interpret(404, "") as Pairing.Redeemed.Bad).message)
        assertEquals(Pairing.UNREACHABLE, (Pairing.interpret(0, "") as Pairing.Redeemed.Bad).message)
        assertEquals(Pairing.NO_TOKEN, (Pairing.interpret(200, "<html></html>") as Pairing.Redeemed.Bad).message)
    }

    @Test
    fun errorsDoNotKeepTheSecret() {
        val secret = "once-secret-1"
        val scrubbed = Pairing.scrub("failed http://10.0.2.2/pair/redeem once-secret-1", secret)
        assertTrue(!scrubbed.contains(secret))
    }

    @Test
    fun qrRoundTripMatchesTheHelperPayload() {
        val payload = """{"v":1,"url":"http://10.0.2.2:8767","secret":"roundtrip-secret","exp":${now + 90}}"""
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 180, 180)
        val pixels = IntArray(180 * 180) { i ->
            val on = matrix.get(i % 180, i / 180)
            if (on) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val source = RGBLuminanceSource(180, 180, pixels)
        val text = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.CHARACTER_SET to "UTF-8")).text
        val read = Pairing.read(text, now) as Pairing.Read.Ok
        assertEquals("roundtrip-secret", read.ticket.secret)
        assertEquals("http://10.0.2.2:8767/pair/redeem", read.ticket.redeemUrl)
    }
}
