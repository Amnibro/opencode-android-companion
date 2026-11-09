// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.InflaterInputStream

/** Reads a non-interlaced 8-bit RGB PNG. Android unit tests have no java.awt ImageIO. */
internal fun decodeRgbPng(bytes: ByteArray): RgbImage {
    val input = DataInputStream(ByteArrayInputStream(bytes))
    val sig = ByteArray(8)
    input.readFully(sig)
    var width = 0
    var height = 0
    val idat = java.io.ByteArrayOutputStream()
    while (true) {
        val len = input.readInt()
        val type = ByteArray(4)
        input.readFully(type)
        val data = ByteArray(len)
        if (len > 0) input.readFully(data)
        input.readInt()
        val name = String(type, Charsets.US_ASCII)
        if (name == "IHDR") {
            val header = DataInputStream(ByteArrayInputStream(data))
            width = header.readInt()
            height = header.readInt()
            val depth = header.readUnsignedByte()
            val color = header.readUnsignedByte()
            val interlace = data[12].toInt()
            require(depth == 8 && color == 2 && interlace == 0) { "unsupported png" }
        } else if (name == "IDAT") {
            idat.write(data)
        } else if (name == "IEND") {
            break
        }
    }
    val inflated = InflaterInputStream(ByteArrayInputStream(idat.toByteArray())).readBytes()
    val bpp = 3
    val stride = width * bpp
    val raw = ByteArray(height * stride)
    var src = 0
    for (y in 0 until height) {
        val filter = inflated[src].toInt() and 0xFF
        src += 1
        val row = y * stride
        for (i in 0 until stride) {
            val value = inflated[src + i].toInt() and 0xFF
            val left = if (i >= bpp) raw[row + i - bpp].toInt() and 0xFF else 0
            val up = if (y > 0) raw[row - stride + i].toInt() and 0xFF else 0
            val ul = if (y > 0 && i >= bpp) raw[row - stride + i - bpp].toInt() and 0xFF else 0
            val out = when (filter) {
                0 -> value
                1 -> value + left
                2 -> value + up
                3 -> value + ((left + up) / 2)
                4 -> value + paeth(left, up, ul)
                else -> error("unsupported png filter $filter")
            }
            raw[row + i] = (out and 0xFF).toByte()
        }
        src += stride
    }
    val pixels = IntArray(width * height)
    for (i in pixels.indices) {
        val o = i * bpp
        val r = raw[o].toInt() and 0xFF
        val g = raw[o + 1].toInt() and 0xFF
        val b = raw[o + 2].toInt() and 0xFF
        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    return RgbImage(width, height, pixels)
}

private fun paeth(left: Int, up: Int, upLeft: Int): Int {
    val p = left + up - upLeft
    val pa = kotlin.math.abs(p - left)
    val pb = kotlin.math.abs(p - up)
    val pc = kotlin.math.abs(p - upLeft)
    return when {
        pa <= pb && pa <= pc -> left
        pb <= pc -> up
        else -> upLeft
    }
}

internal data class RgbImage(val width: Int, val height: Int, val pixels: IntArray)
