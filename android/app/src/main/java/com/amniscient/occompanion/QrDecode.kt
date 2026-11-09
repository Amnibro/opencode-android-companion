// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * ZXing 3.5 QRCodeReader does not apply [DecodeHintType.ALSO_INVERTED] itself.
 * OpenCode's TUI draws the pairing code as light modules on a dark cell, so a
 * camera photo of that code only decodes after the luminance is inverted.
 */
object QrDecode {
    data class Hit(val text: String, val inverted: Boolean)

    fun decode(pixels: IntArray, width: Int, height: Int): Hit? {
        if (width < 21 || height < 21 || pixels.size < width * height) return null
        var current = pixels
        var cw = width
        var ch = height
        repeat(4) {
            read(current, cw, ch)?.let { return Hit(it, false) }
            val flipped = IntArray(current.size) { current[it] xor 0x00FFFFFF }
            read(flipped, cw, ch)?.let { return Hit(it, true) }
            val turned = IntArray(current.size)
            for (y in 0 until ch) {
                for (x in 0 until cw) {
                    turned[x * ch + (ch - 1 - y)] = current[y * cw + x]
                }
            }
            current = turned
            val swap = cw
            cw = ch
            ch = swap
        }
        return null
    }

    private fun read(pixels: IntArray, width: Int, height: Int): String? {
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.ALSO_INVERTED to true,
        )
        val source = RGBLuminanceSource(width, height, pixels)
        for (bitmap in listOf(
            BinaryBitmap(HybridBinarizer(source)),
            BinaryBitmap(GlobalHistogramBinarizer(source)),
        )) {
            try {
                val text = QRCodeReader().decode(bitmap, hints).text
                if (!text.isNullOrBlank()) return text
            } catch (_: Exception) {
            }
        }
        return null
    }
}
