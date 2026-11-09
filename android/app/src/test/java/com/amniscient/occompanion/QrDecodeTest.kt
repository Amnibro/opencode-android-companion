// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrDecodeTest {
    @Test
    fun invertedAngledTuiQrDecodesOnlyAfterInvert() {
        val image = load("tui-inverted-angled.png")
        assertNull(plain(image.pixels, image.width, image.height))
        val hit = QrDecode.decode(image.pixels, image.width, image.height)
        assertNotNull(hit)
        assertTrue(hit!!.inverted)
        assertTrue(hit.text.contains("SAMPLECODE1234567890AB"))
        assertTrue(hit.text.contains("10.0.2.2"))
    }

    @Test
    fun ordinaryAngledQrStillDecodesWithoutInvert() {
        val image = load("ordinary-angled.png")
        val hit = QrDecode.decode(image.pixels, image.width, image.height)
        assertNotNull(hit)
        assertEquals(false, hit!!.inverted)
        assertTrue(hit.text.contains("SAMPLECODE1234567890AB"))
    }

    private fun load(name: String): RgbImage {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
        assertNotNull(stream)
        return decodeRgbPng(stream!!.readBytes())
    }

    private fun plain(pixels: IntArray, width: Int, height: Int): String? = try {
        QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))),
            mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)),
        ).text
    } catch (_: Exception) {
        null
    }
}
